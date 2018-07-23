package ru.vkatzyn.geofencing_test_app;

import android.content.AsyncTaskLoader;
import android.content.Context;

import java.util.ArrayList;

public class GeodataLoader extends AsyncTaskLoader {

    String url;

    public GeodataLoader(Context context, String url) {
        super(context);
        this.url = url;
    }
    @Override
    public ArrayList<Geodata> loadInBackground() {
        if (url == null || url.length() == 0)
            return null;
        return QueryUtils.fetchGeodata(url);
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
    }
}
