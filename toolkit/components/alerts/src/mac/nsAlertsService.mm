/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Growl implementation of nsIAlertsService.
 *
 * The Initial Developer of the Original Code is
 *   Shawn Wilsher <me@shawnwilsher.com>.
 * Portions created by the Initial Developer are Copyright (C) 2006-2007
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

#include "nsAlertsService.h"
#include "nsStringAPI.h"
#include "nsAlertsImageLoadListener.h"
#include "nsIURI.h"
#include "nsIStreamLoader.h"
#include "nsNetUtil.h"
#include "nsCRT.h"
#include "nsCOMPtr.h"
#include "nsIStringBundle.h"
#include "nsIObserverService.h"

#import "mozGrowlDelegate.h"
#import "GrowlApplicationBridge.h"

NS_IMPL_THREADSAFE_ADDREF(nsAlertsService)
NS_IMPL_THREADSAFE_RELEASE(nsAlertsService)

NS_INTERFACE_MAP_BEGIN(nsAlertsService)
  NS_INTERFACE_MAP_ENTRY_AMBIGUOUS(nsISupports, nsIAlertsService)
  NS_INTERFACE_MAP_ENTRY(nsIObserver)
  NS_INTERFACE_MAP_ENTRY(nsIAlertsService)
NS_INTERFACE_MAP_END_THREADSAFE

struct GrowlDelegateWrapper
{
  mozGrowlDelegate* delegate;

  GrowlDelegateWrapper()
  {
    delegate = [[mozGrowlDelegate alloc] init];
  }

  ~GrowlDelegateWrapper()
  {
    [delegate release];
  }
};

nsresult
nsAlertsService::Init()
{
  if ([GrowlApplicationBridge isGrowlInstalled] == NO)
    return NS_ERROR_SERVICE_NOT_AVAILABLE;

  nsresult rv;
  nsCOMPtr<nsIObserverService> os =
    do_GetService("@mozilla.org/observer-service;1", &rv);
  NS_ENSURE_SUCCESS(rv, rv);

  return os->AddObserver(this, "final-ui-startup", PR_FALSE);
}

nsAlertsService::nsAlertsService() : mDelegate(nsnull) {}

nsAlertsService::~nsAlertsService()
{
  if (mDelegate)
    delete mDelegate;
}

NS_IMETHODIMP
nsAlertsService::ShowAlertNotification(const nsAString& aImageUrl,
                                       const nsAString& aAlertTitle,
                                       const nsAString& aAlertText,
                                       PRBool aAlertClickable,
                                       const nsAString& aAlertCookie,
                                       nsIObserver* aAlertListener)
{
  NS_ASSERTION(mDelegate->delegate == [GrowlApplicationBridge growlDelegate],
               "Growl Delegate was not registered properly.");

  PRUint32 ind = 0;
  if (aAlertListener)
    ind = [mDelegate->delegate addObserver: aAlertListener];

  nsresult rv;
  nsCOMPtr<nsIStringBundleService> bundleService =
    do_GetService("@mozilla.org/intl/stringbundle;1", &rv);
  
  // We don't want to fail just yet if we can't get the alert name
  nsString name = NS_LITERAL_STRING("General Notification");
  if (NS_SUCCEEDED(rv)) {
    nsCOMPtr<nsIStringBundle> bundle;
    rv = bundleService->CreateBundle(GROWL_STRING_BUNDLE_LOCATION,
                                     getter_AddRefs(bundle));
    if (NS_SUCCEEDED(rv)) {
      rv = bundle->GetStringFromName(NS_LITERAL_STRING("general").get(),
                                     getter_Copies(name));
      if (NS_FAILED(rv))
        name = NS_LITERAL_STRING("General Notification");
    }
  }

  nsCOMPtr<nsIURI> uri;
  rv = NS_NewURI(getter_AddRefs(uri), aImageUrl);
  if (NS_FAILED(rv)) {
    // image uri failed to resolve, so dispatch to growl with no image
    [mozGrowlDelegate notifyWithName: name
                               title: aAlertTitle
                         description: aAlertText
                            iconData: [NSData data]
                                 key: ind
                              cookie: aAlertCookie];

    return NS_OK;
  }

  nsCOMPtr<nsAlertsImageLoadListener> listener =
    new nsAlertsImageLoadListener(name, aAlertTitle, aAlertText,
                                  aAlertClickable, aAlertCookie, ind);

  nsCOMPtr<nsIStreamLoader> loader;
  rv = NS_NewStreamLoader(getter_AddRefs(loader), uri, listener);
  NS_ENSURE_SUCCESS(rv, rv);

  return NS_OK;
}

NS_IMETHODIMP
nsAlertsService::Observe(nsISupports* aSubject, const char* aTopic,
                         const PRUnichar* aData)
{
  if (nsCRT::strcmp(aTopic, "final-ui-startup") == 0) {
    NS_ASSERTION([GrowlApplicationBridge growlDelegate] == nil,
                 "We already registered with Growl!");

    mDelegate = new GrowlDelegateWrapper();

    // registers with Growl
    [GrowlApplicationBridge setGrowlDelegate: mDelegate->delegate];
  }

  return NS_OK;
}

