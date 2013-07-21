package de.lmu.mcm.helper;

import java.util.UUID;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.telephony.TelephonyManager;

/**
 * Helper class that faciliates the usage of SharedPreferences. Used to store settings.
 * 
 * @author Thomas Bornschlegel
 * 
 */
public class PrefsHelper {

    private static String TAG = "PrefsHelper";
    private static String PREFS_NAME = "lmu multichannel middleware preferences";
    private static String KEY_PREFIX_PUBLIC_KEY = "public key of: ";
    private static String KEY_OWN_ID = "own id";
    private static String KEY_REMOTE_ID = "remote id";
    private static String KEY_MOBILE_NUMBER_TO_ID_MAPPING = "mobile number: ";
    private static String KEY_ID_TO_MOBILE_NUMBER_MAPPING = "id for mobile: ";
    private static String KEY_ID_BLUETOOTH_ADDRESS = "id for bluetooth: ";

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, 0);
    }

    /**
     * Stores the filename for a public key in sharedPreferences.
     * */
    public static void storeFilenamePublicKey(Context context, String userid, String filenamePublicKey) {
        PrefsHelper.storeString(context, KEY_PREFIX_PUBLIC_KEY + userid, filenamePublicKey);
    }

    /**
     * @return the filename for a public key from sharedPreferences.
     * */
    public static String getFilenamePublicKey(Context context, UUID uuidOfUser) {
        return getSharedPreferences(context).getString(KEY_PREFIX_PUBLIC_KEY + uuidOfUser.toString(), null);
    }

    /**
     * Stores the mobile number of a for a public key in sharedPreferences.
     * */
    public static void storeMobileNumberToUserIdMapping(Context context, String mobileNumber, UUID uuidOfUser) {
        String normalizesNumber = normalizeMobilePhoneNumer(mobileNumber);
        LogHelper.getInstance().d(TAG, "Normalized phone number from " + mobileNumber + " to " + normalizesNumber);
        PrefsHelper.storeString(context, KEY_MOBILE_NUMBER_TO_ID_MAPPING + normalizesNumber, uuidOfUser.toString());
        PrefsHelper.storeString(context, KEY_ID_TO_MOBILE_NUMBER_MAPPING + uuidOfUser, normalizesNumber);
    }

    /**
     * @return the mobile number of the given user from sharedPreferences.
     * */
    public static String getMobileNumberFromUserId(Context context, String uuidOfUser) {
        return getSharedPreferences(context).getString(KEY_ID_TO_MOBILE_NUMBER_MAPPING + uuidOfUser, null);
    }

    /**
     * @return the user id that matches the given mobilenumber from sharedPreferences.
     * */
    public static String getUserIdForMobileNumber(Context context, String mobileNumber) {
        String normalizesNumber = normalizeMobilePhoneNumer(mobileNumber);
        return getSharedPreferences(context).getString(KEY_MOBILE_NUMBER_TO_ID_MAPPING + normalizesNumber, null);
    }

    /**
     * Stores the bluetooth address of the users mobile device in sharedPreferences.
     * */
    public static void storeBluetoothAddressForUser(Context context, byte[] bluetoothAddress, UUID uuidOfUser) {
        if (bluetoothAddress != null) {
            String base64 = ByteConverter.encodeAsBase64String(bluetoothAddress);
            PrefsHelper.storeString(context, KEY_ID_BLUETOOTH_ADDRESS + uuidOfUser.toString(), base64);
        }
    }

    /**
     * @return the bluetooth address of the given user from sharedPreferences.
     * */
    public static byte[] getBluetoothAddressForUser(Context context, String userId) {
        String base64 = getSharedPreferences(context).getString(KEY_ID_BLUETOOTH_ADDRESS + userId, null);
        if (base64 != null) {
            return ByteConverter.decodeBase64String(base64);
        }
        return null;
    }

    /**
     * Stores the UUID of the current communication partner in sharedPreferences.
     * */
    public static void storeIdOfCommunicationPartner(UUID uuid, Context context) {
        storeString(context, KEY_REMOTE_ID, uuid.toString());
    }

    /**
     * @return the uuid of the current communication partner from sharedPreferences.
     * */
    public static String getIdOfCommunicationPartner(Context context) {
        return getSharedPreferences(context).getString(KEY_REMOTE_ID, null);
    }

    /**
     * Checks if the own uuid was already generated. Generates it, if it is not present.
     */
    public static void generateOwnIdIfNotPresent(Context context) {
        if (PrefsHelper.getOwnId(context) == null) {
            UUID uuid = UUID.randomUUID();
            storeString(context, KEY_OWN_ID, uuid.toString());
            LogHelper.getInstance().d(TAG, "Generated new own id: " + uuid.toString());

        }
    }

    /**
     * @return the own if from sharedPreferences.
     * */
    public static String getOwnId(Context context) {
        return getSharedPreferences(context).getString(KEY_OWN_ID, null);
    }

    /**
     * @return the own telephone number
     * */
    public static String getOwnTelephoneNumber(Context context) {
        TelephonyManager telephonyMgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyMgr != null) {
            String telephoneNumber = telephonyMgr.getLine1Number();
            if (telephoneNumber != null) {
                String normalizedNumber = normalizeMobilePhoneNumer(telephoneNumber);
                return normalizedNumber;
            } else {
                LogHelper.getInstance().d(TAG, "TelephoneManager.getLine1Number returns null.");
            }
        }
        return null;
    }

    private static void storeString(Context context, String key, String value) {
        SharedPreferences prefs = getSharedPreferences(context);
        Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.commit();
    }

    /**
     * Normalizes the given mobile number, so that e.g. +0049123456789 and +49123456789 return the same result.
     * 
     * @param a
     *            valid phone number
     * @result a phone number of the form 00491234567
     * */
    public static String normalizeMobilePhoneNumer(String mobilePhoneNumber) {

        mobilePhoneNumber = mobilePhoneNumber.replaceAll(" ", "");
        mobilePhoneNumber = mobilePhoneNumber.replaceAll("\\(", "");
        mobilePhoneNumber = mobilePhoneNumber.replaceAll("\\)", "");
        mobilePhoneNumber = mobilePhoneNumber.replaceAll("\\/", "");
        mobilePhoneNumber = mobilePhoneNumber.replaceAll("\\-", "");

        if (mobilePhoneNumber.contains("+"))
            mobilePhoneNumber = mobilePhoneNumber.replace("+", "00");
        if (mobilePhoneNumber.startsWith("00")) {
            mobilePhoneNumber = removeZeroFromNumber(mobilePhoneNumber);
            return mobilePhoneNumber;
        }
        if (mobilePhoneNumber.startsWith("0"))
            mobilePhoneNumber = mobilePhoneNumber.replaceFirst("0", "0049");

        mobilePhoneNumber = removeZeroFromNumber(mobilePhoneNumber);
        mobilePhoneNumber = mobilePhoneNumber.replaceAll(" ", "");

        return mobilePhoneNumber;
    }

    private static String removeZeroFromNumber(String mobilePhoneNumber) {
        if (mobilePhoneNumber.startsWith("00490")) {
            mobilePhoneNumber = mobilePhoneNumber.replaceFirst("00490", "0049");
        }
        return mobilePhoneNumber;
    }

}