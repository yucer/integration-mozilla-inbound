/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef mozilla_net_WyciwygChannelParent_h
#define mozilla_net_WyciwygChannelParent_h

#include "mozilla/net/PWyciwygChannelParent.h"
#include "mozilla/net/NeckoCommon.h"
#include "nsIStreamListener.h"

#include "nsIWyciwygChannel.h"
#include "nsIInterfaceRequestor.h"
#include "nsILoadContext.h"

namespace mozilla {
namespace net {

class WyciwygChannelParent : public PWyciwygChannelParent
                           , public nsIStreamListener
                           , public nsIInterfaceRequestor
{
public:
  NS_DECL_ISUPPORTS
  NS_DECL_NSIREQUESTOBSERVER
  NS_DECL_NSISTREAMLISTENER
  NS_DECL_NSIINTERFACEREQUESTOR

  WyciwygChannelParent();
  virtual ~WyciwygChannelParent();

protected:
  virtual bool RecvInit(const URIParams& uri);
  virtual bool RecvAsyncOpen(const URIParams& original,
                             const uint32_t& loadFlags,
                             const IPC::SerializedLoadContext& loadContext);
  virtual bool RecvWriteToCacheEntry(const nsString& data);
  virtual bool RecvCloseCacheEntry(const nsresult& reason);
  virtual bool RecvSetCharsetAndSource(const int32_t& source,
                                       const nsCString& charset);
  virtual bool RecvSetSecurityInfo(const nsCString& securityInfo);
  virtual bool RecvCancel(const nsresult& statusCode);

  virtual void ActorDestroy(ActorDestroyReason why);

  nsCOMPtr<nsIWyciwygChannel> mChannel;
  bool mIPCClosed;
  nsCOMPtr<nsILoadContext> mLoadContext;
};

} // namespace net
} // namespace mozilla

#endif // mozilla_net_WyciwygChannelParent_h
