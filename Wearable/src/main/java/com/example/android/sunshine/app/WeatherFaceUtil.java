package com.example.android.sunshine.app;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by Katrina on 21/05/2016.
 */
public class WeatherFaceUtil {
    private static final String TAG = "WeatherFaceUtil";
    public static final String WEAR_DATA_PATH = "/sunshine";
    public static final String WEAR_DATA_HIGH = "high";
    public static final String WEAR_DATA_LOW = "low";
    public static final String WEAR_DATA_TYPE = "type";

    public static final String DEFAULT_HIGH = "15C";
    public static final String DEFAULT_LOW = "12C";
    public static final int DEFAULT_TYPE = 1;


    public static String getStringForWeatherCondition(Context context, int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        int stringId;
        if (weatherId >= 200 && weatherId <= 232) {
            stringId = R.string.condition_2xx;
        } else if (weatherId >= 300 && weatherId <= 321) {
            stringId = R.string.condition_3xx;
        } else switch (weatherId) {
            case 500:
                stringId = R.string.condition_500;
                break;
            case 501:
                stringId = R.string.condition_501;
                break;
            case 502:
                stringId = R.string.condition_502;
                break;
            case 503:
                stringId = R.string.condition_503;
                break;
            case 504:
                stringId = R.string.condition_504;
                break;
            case 511:
                stringId = R.string.condition_511;
                break;
            case 520:
                stringId = R.string.condition_520;
                break;
            case 531:
                stringId = R.string.condition_531;
                break;
            case 600:
                stringId = R.string.condition_600;
                break;
            case 601:
                stringId = R.string.condition_601;
                break;
            case 602:
                stringId = R.string.condition_602;
                break;
            case 611:
                stringId = R.string.condition_611;
                break;
            case 612:
                stringId = R.string.condition_612;
                break;
            case 615:
                stringId = R.string.condition_615;
                break;
            case 616:
                stringId = R.string.condition_616;
                break;
            case 620:
                stringId = R.string.condition_620;
                break;
            case 621:
                stringId = R.string.condition_621;
                break;
            case 622:
                stringId = R.string.condition_622;
                break;
            case 701:
                stringId = R.string.condition_701;
                break;
            case 711:
                stringId = R.string.condition_711;
                break;
            case 721:
                stringId = R.string.condition_721;
                break;
            case 731:
                stringId = R.string.condition_731;
                break;
            case 741:
                stringId = R.string.condition_741;
                break;
            case 751:
                stringId = R.string.condition_751;
                break;
            case 761:
                stringId = R.string.condition_761;
                break;
            case 762:
                stringId = R.string.condition_762;
                break;
            case 771:
                stringId = R.string.condition_771;
                break;
            case 781:
                stringId = R.string.condition_781;
                break;
            case 800:
                stringId = R.string.condition_800;
                break;
            case 801:
                stringId = R.string.condition_801;
                break;
            case 802:
                stringId = R.string.condition_802;
                break;
            case 803:
                stringId = R.string.condition_803;
                break;
            case 804:
                stringId = R.string.condition_804;
                break;
            case 900:
                stringId = R.string.condition_900;
                break;
            case 901:
                stringId = R.string.condition_901;
                break;
            case 902:
                stringId = R.string.condition_902;
                break;
            case 903:
                stringId = R.string.condition_903;
                break;
            case 904:
                stringId = R.string.condition_904;
                break;
            case 905:
                stringId = R.string.condition_905;
                break;
            case 906:
                stringId = R.string.condition_906;
                break;
            case 951:
                stringId = R.string.condition_951;
                break;
            case 952:
                stringId = R.string.condition_952;
                break;
            case 953:
                stringId = R.string.condition_953;
                break;
            case 954:
                stringId = R.string.condition_954;
                break;
            case 955:
                stringId = R.string.condition_955;
                break;
            case 956:
                stringId = R.string.condition_956;
                break;
            case 957:
                stringId = R.string.condition_957;
                break;
            case 958:
                stringId = R.string.condition_958;
                break;
            case 959:
                stringId = R.string.condition_959;
                break;
            case 960:
                stringId = R.string.condition_960;
                break;
            case 961:
                stringId = R.string.condition_961;
                break;
            case 962:
                stringId = R.string.condition_962;
                break;
            default:
                return context.getString(R.string.condition_unknown, weatherId);
        }
        return context.getString(stringId);
    }


    public interface FetchConfigDataMapCallback {
        void onConfigDataMapFetched(DataMap config);
    }

    public static void fetchConfigDataMap(final GoogleApiClient client,
                                          final FetchConfigDataMapCallback callback) {

        Wearable.NodeApi.getLocalNode(client).setResultCallback(new ResultCallback<NodeApi.GetLocalNodeResult>() {
            @Override
            public void onResult(@NonNull NodeApi.GetLocalNodeResult getLocalNodeResult) {
                String localNode = getLocalNodeResult.getNode().getId();
                Uri uri = new Uri.Builder().scheme("wear").path(WEAR_DATA_PATH).authority(localNode).build();
                Wearable.DataApi.getDataItem(client, uri).setResultCallback(new DataItemResultCallback(callback));
            }
        });
    }


    public static void overwriteKeysInConfigDataMap(final GoogleApiClient client, final DataMap configKeysToOverwrite) {
        fetchConfigDataMap(client, new FetchConfigDataMapCallback() {
            @Override
            public void onConfigDataMapFetched(DataMap config) {
                DataMap overwrittenConfig = new DataMap();
                overwrittenConfig.putAll(config);
                overwrittenConfig.putAll(configKeysToOverwrite);
                putConfigDataItem(client, overwrittenConfig);
            }
        });
    }

    public static void putConfigDataItem(GoogleApiClient client, DataMap newConfig) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEAR_DATA_PATH);
        DataMap configToPut = putDataMapRequest.getDataMap();
        configToPut.putAll(newConfig);
        Wearable.DataApi.putDataItem(client, putDataMapRequest.asPutDataRequest()).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "PutDataItem result" + dataItemResult.getStatus());
                }
            }
        });
    }

    public static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult>
    {
        private final FetchConfigDataMapCallback mCallback;

        public DataItemResultCallback(FetchConfigDataMapCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
            if(dataItemResult.getStatus().isSuccess())
            {
                if(dataItemResult.getDataItem()!=null)
                {
                    DataItem configDataItem=dataItemResult.getDataItem();
                    DataMapItem dataMapItem=DataMapItem.fromDataItem(configDataItem);
                    DataMap config=dataMapItem.getDataMap();
                    mCallback.onConfigDataMapFetched(config);
                }
                else
                {
                    mCallback.onConfigDataMapFetched(new DataMap());
                }
            }
        }
    }
}
