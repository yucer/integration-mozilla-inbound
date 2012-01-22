/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * ***** BEGIN LICENSE BLOCK *****
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
 * The Original Code is Mozilla Android code.
 *
 * The Initial Developer of the Original Code is Mozilla Foundation.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Brad Lassey <blassey@mozilla.com>
 *   Lucas Rocha <lucasr@mozilla.com>
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

package org.mozilla.gecko;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.gecko.db.BrowserDB;
import org.mozilla.gecko.db.BrowserDB.URLColumns;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class AboutHomeContent extends ScrollView {
    private static final String LOGTAG = "GeckoAboutHome";

    private static final int NUMBER_OF_TOP_SITES_PORTRAIT = 4;
    private static final int NUMBER_OF_TOP_SITES_LANDSCAPE = 3;

    private static final int NUMBER_OF_COLS_PORTRAIT = 2;
    private static final int NUMBER_OF_COLS_LANDSCAPE = 3;

    private Cursor mCursor;
    UriLoadCallback mUriLoadCallback = null;
    private LayoutInflater mInflater;

    protected SimpleCursorAdapter mTopSitesAdapter;
    protected GridView mTopSitesGrid;

    protected LinearLayout mAddonsLayout;
    protected LinearLayout mLastTabsLayout;

    public interface UriLoadCallback {
        public void callback(String uriSpec);
    }

    public AboutHomeContent(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScrollContainer(true);
        setBackgroundResource(R.drawable.abouthome_bg_repeat);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        synchronized (this) {
            if (mTopSitesGrid != null && mAddonsLayout != null && mLastTabsLayout != null)
                return;

            mTopSitesGrid = (GridView)findViewById(R.id.top_sites_grid);
            mTopSitesGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    Cursor c = (Cursor) parent.getItemAtPosition(position);

                    String spec = c.getString(c.getColumnIndex(URLColumns.URL));
                    Log.i(LOGTAG, "clicked: " + spec);

                    if (mUriLoadCallback != null)
                        mUriLoadCallback.callback(spec);
                }
            });

            mAddonsLayout = (LinearLayout) findViewById(R.id.recommended_addons);
            mLastTabsLayout = (LinearLayout) findViewById(R.id.last_tabs);

            TextView allTopSitesText = (TextView) findViewById(R.id.all_top_sites_text);
            allTopSitesText.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GeckoApp.mAppContext.showAwesomebar(AwesomeBar.Type.EDIT);
                }
            });

            TextView allAddonsText = (TextView) findViewById(R.id.all_addons_text);
            allAddonsText.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (mUriLoadCallback != null)
                        mUriLoadCallback.callback("about:addons");
                }
            });
        }
    }

    private int getNumberOfTopSites() {
        Configuration config = getContext().getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
            return NUMBER_OF_TOP_SITES_LANDSCAPE;
        else
            return NUMBER_OF_TOP_SITES_PORTRAIT;
    }

    private int getNumberOfColumns() {
        Configuration config = getContext().getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
            return NUMBER_OF_COLS_LANDSCAPE;
        else
            return NUMBER_OF_COLS_PORTRAIT;
    }

    void init(final Activity activity) {
        mInflater.inflate(R.layout.abouthome_content, this);
        final Runnable generateCursorsRunnable = new Runnable() {
            public void run() {
                if (mCursor != null)
                    activity.stopManagingCursor(mCursor);

                ContentResolver resolver = GeckoApp.mAppContext.getContentResolver();
                mCursor = BrowserDB.getTopSites(resolver, NUMBER_OF_TOP_SITES_PORTRAIT);
                activity.startManagingCursor(mCursor);

                mTopSitesAdapter = new TopSitesCursorAdapter(activity,
                                                             R.layout.abouthome_topsite_item,
                                                             mCursor,
                                                             new String[] { URLColumns.TITLE,
                                                                            URLColumns.THUMBNAIL },
                                                             new int[] { R.id.title, R.id.thumbnail });

                GeckoApp.mAppContext.mMainHandler.post(new Runnable() {
                    public void run() {
                        mTopSitesGrid.setNumColumns(getNumberOfColumns());

                        mTopSitesGrid.setAdapter(mTopSitesAdapter);
                        mTopSitesAdapter.setViewBinder(new TopSitesViewBinder());
                    }
                });

                GeckoAppShell.getHandler().post(new Runnable() {
                    public void run() {
                        readLastTabs(activity);
                        readRecommendedAddons(activity);
                    }
                });
            }
        };
        Runnable finishInflateRunnable = new Runnable() {
            public void run() {
                onFinishInflate();
                GeckoAppShell.getHandler().post(generateCursorsRunnable);
            }
        };
        GeckoApp.mAppContext.mMainHandler.post(finishInflateRunnable);
    }

    public void setUriLoadCallback(UriLoadCallback uriLoadCallback) {
        mUriLoadCallback = uriLoadCallback;
    }

    public void onActivityContentChanged(Activity activity) {
        GeckoApp.mAppContext.mMainHandler.post(new Runnable() {
            public void run() {
                mTopSitesGrid.setAdapter(mTopSitesAdapter);
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mTopSitesGrid != null) 
            mTopSitesGrid.setNumColumns(getNumberOfColumns());
        if (mTopSitesAdapter != null)
            mTopSitesAdapter.notifyDataSetChanged();

        super.onConfigurationChanged(newConfig);
    }

    private String readJSONFile(Activity activity, String filename) {
        String json = null;
        InputStream fileStream = null;
        File profileDir = GeckoApp.mAppContext.getProfileDir();

        if (profileDir == null)
            return null;

        File recommendedAddonsFile = new File(profileDir, filename);
        if (recommendedAddonsFile.exists()) {
            try {
                fileStream = new FileInputStream(recommendedAddonsFile);
            } catch (FileNotFoundException fnfe) {}
        }
        if (fileStream == null)
            return null;

        return readStringFromStream(fileStream);
    }

    private String readFromZipFile(Activity activity, String filename) {
        ZipFile zip = null;
        String str = null;
        try {
            InputStream fileStream = null;
            File applicationPackage = new File(activity.getApplication().getPackageResourcePath());
            zip = new ZipFile(applicationPackage);
            if (zip == null)
                return null;
            ZipEntry fileEntry = zip.getEntry(filename);
            if (fileEntry == null)
                return null;
            fileStream = zip.getInputStream(fileEntry);
            str = readStringFromStream(fileStream);
        } catch (IOException ioe) {
            Log.e(LOGTAG, "error reading zip file: " + filename, ioe);
        } finally {
            try {
                if (zip != null)
                    zip.close();
            } catch (IOException ioe) {
                // catch this here because we can continue even if the
                // close failed
                Log.e(LOGTAG, "error closing zip filestream", ioe);
            }
        }
        return str;
    }

    private String readStringFromStream(InputStream fileStream) {
        String str = null;
        try {
            byte[] buf = new byte[32768];
            StringBuffer jsonString = new StringBuffer();
            int read = 0;
            while ((read = fileStream.read(buf, 0, 32768)) != -1)
                jsonString.append(new String(buf, 0, read));
            str = jsonString.toString();
        } catch (IOException ioe) {
            Log.i(LOGTAG, "error reading filestream", ioe);
        } finally {
            try {
                if (fileStream != null)
                    fileStream.close();
            } catch (IOException ioe) {
                // catch this here because we can continue even if the
                // close failed
                Log.e(LOGTAG, "error closing filestream", ioe);
            }
        }
        return str;
    }

    private void readRecommendedAddons(final Activity activity) {
        final String addonsFilename = "recommended-addons.json";
        String jsonString = readJSONFile(activity, addonsFilename);
        if (jsonString == null) {
            Log.i("Addons", "filestream is null");
            jsonString = readFromZipFile(activity, addonsFilename);
        }
        if (jsonString == null)
            return;

        final JSONArray array;
        try {
            array = new JSONObject(jsonString).getJSONArray("addons");
        } catch (JSONException e) {
            Log.i(LOGTAG, "error reading json file", e);
            return;
        }

        GeckoApp.mAppContext.mMainHandler.post(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject jsonobj = array.getJSONObject(i);
                        View row = mInflater.inflate(R.layout.abouthome_addon_row, mAddonsLayout, false);
                        ((TextView) row.findViewById(R.id.addon_title)).setText(jsonobj.getString("name"));
                        mAddonsLayout.addView(row);
                    }
                } catch (JSONException e) {
                    Log.i(LOGTAG, "error reading json file", e);
                }
            }
        });
    }

    private void readLastTabs(final Activity activity) {
        final String sessionFilename = "sessionstore.js";
        final JSONArray tabs;
        String jsonString = readJSONFile(activity, sessionFilename);
        if (jsonString == null)
            return;

        try {
            tabs = new JSONObject(jsonString).getJSONArray("windows")
                                             .getJSONObject(0)
                                             .getJSONArray("tabs");
        } catch (JSONException e) {
            Log.i(LOGTAG, "error reading json file", e);
            return;
        }

        final ArrayList<String> lastTabUrlsList = new ArrayList<String>();

        for (int i = 0; i < tabs.length(); i++) {
            final String title;
            final String url;
            try {
                JSONObject tab = tabs.getJSONObject(i);
                int index = tab.getInt("index");
                JSONArray entries = tab.getJSONArray("entries");
                JSONObject entry = entries.getJSONObject(index - 1);
                title = entry.getString("title");
                url = entry.getString("url");
            } catch (JSONException e) {
                Log.e(LOGTAG, "error reading json file", e);
                continue;
            }

            // don't show last tabs for about pages
            if (url.startsWith("about:"))
                continue;

            ContentResolver resolver = GeckoApp.mAppContext.getContentResolver();
            final BitmapDrawable favicon = BrowserDB.getFaviconForUrl(resolver, url);
            lastTabUrlsList.add(url);

            GeckoApp.mAppContext.mMainHandler.post(new Runnable() {
                public void run() {
                    View container = mInflater.inflate(R.layout.abouthome_last_tabs_row, mLastTabsLayout, false);
                    ((TextView) container.findViewById(R.id.last_tab_title)).setText(title);
                    ((TextView) container.findViewById(R.id.last_tab_url)).setText(url);
                    if (favicon != null)
                        ((ImageView) container.findViewById(R.id.last_tab_favicon)).setImageDrawable(favicon);

                    container.findViewById(R.id.last_tab_row).setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            GeckoApp.mAppContext.loadUrlInTab(url);
                        }
                    });

                    mLastTabsLayout.addView(container);
                }
            });
        }

        int numLastTabs = lastTabUrlsList.size();
        if (numLastTabs > 0) {
            GeckoApp.mAppContext.mMainHandler.post(new Runnable() {
                public void run() {
                    findViewById(R.id.last_tabs_title).setVisibility(View.VISIBLE);
                }
            });

            if (numLastTabs > 1) {
                GeckoApp.mAppContext.mMainHandler.post(new Runnable() {
                    public void run() {
                        LinkTextView openAll = (LinkTextView) findViewById(R.id.last_tabs_open_all);
                        openAll.setVisibility(View.VISIBLE);
                        openAll.setOnClickListener(new LinkTextView.OnClickListener() {
                            public void onClick(View v) {
                                for (String url : lastTabUrlsList)
                                    GeckoApp.mAppContext.loadUrlInTab(url);
                            }
                        });
                    }
                });
            }
        }
    }

    public static class TopSitesGridView extends GridView {
        /** From layout xml:
         *  80dip image height 
         * + 2dip image paddingTop
         * + 1dip image padding (for bottom)
         * + 3dip marginTop on the TextView
         * +15dip TextView height
         * + 8dip vertical spacing in the GridView
         * ------
         * 109dip total height per top site grid item
         */
        private static final int kTopSiteItemHeight = 109;
        float mDisplayDensity ;

        public TopSitesGridView(Context context, AttributeSet attrs) {
            super(context, attrs);
            DisplayMetrics dm = new DisplayMetrics();
            GeckoApp.mAppContext.getWindowManager().getDefaultDisplay().getMetrics(dm);
            mDisplayDensity = dm.density;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int numCols;
            int numRows;
            Configuration config = getContext().getResources().getConfiguration();
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                numCols = NUMBER_OF_COLS_LANDSCAPE;
                numRows = NUMBER_OF_TOP_SITES_LANDSCAPE / NUMBER_OF_COLS_LANDSCAPE;
            } else {
                numCols = NUMBER_OF_COLS_PORTRAIT;
                numRows = NUMBER_OF_TOP_SITES_PORTRAIT / NUMBER_OF_COLS_PORTRAIT;
            }
            int expandedHeightSpec = 
                MeasureSpec.makeMeasureSpec((int)(mDisplayDensity * numRows * kTopSiteItemHeight),
                                            MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, expandedHeightSpec);
        }
    }

    public class TopSitesCursorAdapter extends SimpleCursorAdapter {
        public TopSitesCursorAdapter(Context context, int layout, Cursor c,
                                     String[] from, int[] to) {
            super(context, layout, c, from, to);
        }

        @Override
        public int getCount() {
            return Math.min(super.getCount(), getNumberOfTopSites());
        }
    }

    class TopSitesViewBinder implements SimpleCursorAdapter.ViewBinder {
        private boolean updateThumbnail(View view, Cursor cursor, int thumbIndex) {
            byte[] b = cursor.getBlob(thumbIndex);
            ImageView thumbnail = (ImageView) view;

            if (b == null) {
                thumbnail.setImageResource(R.drawable.abouthome_topsite_placeholder);
            } else {
                try {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(b, 0, b.length);
                    thumbnail.setImageBitmap(bitmap);
                } catch (OutOfMemoryError oom) {
                    Log.e(LOGTAG, "Unable to load thumbnail bitmap", oom);
                    thumbnail.setImageResource(R.drawable.abouthome_topsite_placeholder);
                }
            }

            return true;
        }

        private boolean updateTitle(View view, Cursor cursor, int titleIndex) {
            String title = cursor.getString(titleIndex);
            TextView titleView = (TextView) view;

            // Use the URL instead of an empty title for consistency with the normal URL
            // bar view - this is the equivalent of getDisplayTitle() in Tab.java
            if (title == null || title.length() == 0) {
                int urlIndex = cursor.getColumnIndexOrThrow(URLColumns.URL);
                title = cursor.getString(urlIndex);
            }

            titleView.setText(title);
            return true;
        }

        @Override
        public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
            int titleIndex = cursor.getColumnIndexOrThrow(URLColumns.TITLE);
            if (columnIndex == titleIndex) {
                return updateTitle(view, cursor, titleIndex);
            }

            int thumbIndex = cursor.getColumnIndexOrThrow(URLColumns.THUMBNAIL);
            if (columnIndex == thumbIndex) {
                return updateThumbnail(view, cursor, thumbIndex);
            }

            // Other columns are handled automatically
            return false;
        }
    }

    public static class LinkTextView extends TextView {
        public LinkTextView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            SpannableString content = new SpannableString(text + " \u00BB");
            content.setSpan(new UnderlineSpan(), 0, text.length(), 0);

            super.setText(content, BufferType.SPANNABLE);
        }
    }
}