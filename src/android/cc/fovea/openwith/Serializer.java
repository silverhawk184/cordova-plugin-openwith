package cc.fovea.openwith;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handle serialization of Android objects ready to be sent to javascript.
 */
class Serializer {

	/** Convert an intent to JSON.
	*
	* This actually only exports stuff necessary to see file content
	* (streams or clip data) sent with the intent.
	* If none are specified, null is return.
	*/
	public static JSONObject toJSONObject(
			final ContentResolver contentResolver,
			final Intent intent)
			throws JSONException {
		JSONArray items = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			items = itemsFromClipData(contentResolver, intent.getClipData());
		}
		if (items == null || items.length() == 0) {
			items = itemsFromExtras(contentResolver, intent.getExtras());
		}
		if (items == null || items.length() == 0) {
			items = itemsFromData(contentResolver, intent.getData());
		}
		if (items == null) {
			return null;
		}
		final JSONObject action = new JSONObject();
		action.put("action", translateAction(intent.getAction()));
		action.put("exit", readExitOnSent(intent.getExtras()));
		action.put("items", items);
		return action;
	}
	
//NS------------DUPLICATE toJSONObject function so to expose the raw intent item -----------	
	public static JSONObject toJSONObjectNS(
			final ContentResolver contentResolver,
			//final Intent intent, //NS added for test 6
			ClipData.Item intentRaw) //NS exposed raw intent item
			throws JSONException {
		
		
		//NS do ".getUri()" to re-create intent variable from original method
		
		//NS TEST1
		//Intent intent = intentRaw.getUri(); 
		// -- cannot convert Uri to Intent
		
		//NS TEST2
		//Intent intent = new Intent();
		//intent = intentRaw.getUri();
		// -- cannot convert Uri to Intent
		
		//NS TEST3
		//Uri intent = new Uri();
		//intent = intentRaw.getUri();
		// -- the code below is looking for an intent and errors with a uri
		
		//NS TEST4
		//Intent intent = intentRaw;
		// -- cannot convert item to intent
		
		//NS TEST5
		//Intent intent = new Intent(intentRaw.getUri());
		// -- error: no suitable constructor found for Intent(Uri)
		
		//NS TEST6
		// see comments for changes (line 56 & 154)
		// -- error: incompatible types: Uri cannot be converted to Intent on line 154
		
		//NS TEST7
		Intent intent = getIntent();
		//Intent intent = new Intent(ContentResolver, intentRaw.getUri());
		// -- 
		
		
		JSONArray items = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			items = itemsFromClipData(contentResolver, intent.getClipData());
		}
		if (items == null || items.length() == 0) {
			items = itemsFromExtras(contentResolver, intent.getExtras());
		}
		if (items == null || items.length() == 0) {
			items = itemsFromData(contentResolver, intent.getData());
		}
		if (items == null) {
			return null;
		}
		final JSONObject action = new JSONObject();
		action.put("action", translateAction(intent.getAction()));
		action.put("exit", readExitOnSent(intent.getExtras()));
		/* //NS  --- from stackoverflow answer ... not ready yet ----
		if (intentRaw.getText() != null) {
			String shareText = intentRaw.getText().toString();
				if (shareText.contains("http:/") || shareText.contains("https:/")) {
					shareText = shareText.substring(shareText.indexOf("http"));
					action.put("link", URI.create(shareText));
				}
			}
		}
		*/
		action.put("items", items);
		return action;
	}

	public static String translateAction(final String action) {
		if ("android.intent.action.SEND".equals(action) ||
			"android.intent.action.SEND_MULTIPLE".equals(action)) {
			return "SEND";
		} else if ("android.intent.action.VIEW".equals(action)) {
			return "VIEW";
		}
		return action;
	}

	/** Read the value of "exit_on_sent" in the intent's extra.
	*
	* Defaults to false. */
	public static boolean readExitOnSent(final Bundle extras) {
		if (extras == null) {
			return false;
		}
		return extras.getBoolean("exit_on_sent", false);
	}

	/** Extract the list of items from clip data (if available).
	*
	* Defaults to null. */
	public static JSONArray itemsFromClipData(
			final ContentResolver contentResolver,
			final ClipData clipData)
			throws JSONException {
		if (clipData != null) {
			final int clipItemCount = clipData.getItemCount();
			JSONObject[] items = new JSONObject[clipItemCount];
			for (int i = 0; i < clipItemCount; i++) {
				//NS -- USE THE NEW FUNCTION THAT TAKES THE RAW Intent item
				//items[i] = toJSONObject(contentResolver, clipData.getItemAt(i).getUri()); //NS original 
				items[i] = toJSONObjectNS(contentResolver, clipData.getItemAt(i)); //NS (tests1-5,7) do the ".getUri()" in the function
				//items[i] = toJSONObjectNS(contentResolver, clipData.getItemAt(i).getUri(), clipData.getItemAt(i)); //NS (test 6)
				
			}
			return new JSONArray(items);
		}
		return null;
	}

	/** Extract the list of items from the intent's extra stream.
	*
	* See Intent.EXTRA_STREAM for details. */
	public static JSONArray itemsFromExtras(
			final ContentResolver contentResolver,
			final Bundle extras)
			throws JSONException {
		if (extras == null) {
			return null;
		}
		final JSONObject item = toJSONObject(
				contentResolver,
				(Uri) extras.get(Intent.EXTRA_STREAM));
		if (item == null) {
			return null;
		}
		final JSONObject[] items = new JSONObject[1];
		items[0] = item;
		return new JSONArray(items);
	}

	/** Extract the list of items from the intent's getData
	*
	* See Intent.ACTION_VIEW for details. */
	public static JSONArray itemsFromData(
			final ContentResolver contentResolver,
			final Uri uri)
			throws JSONException {
		if (uri == null) {
			return null;
		}
		final JSONObject item = toJSONObject(
				contentResolver,
				uri);
		if (item == null) {
			return null;
		}
		final JSONObject[] items = new JSONObject[1];
		items[0] = item;
		return new JSONArray(items);
	}

	/** Convert an Uri to JSON object.
	*
	* Object will include:
	*    "type" of data;
	*    "uri" itself;
	*    "path" to the file, if applicable.
	*    "data" for the file.
	*/
	public static JSONObject toJSONObject(
			final ContentResolver contentResolver,
			final Uri uri)
			throws JSONException {
		if (uri == null) {
			return null;
		}
		final JSONObject json = new JSONObject();
		final String type = contentResolver.getType(uri);
		json.put("type", type);
		json.put("uri", uri);
		json.put("path", getRealPathFromURI(contentResolver, uri));
		return json;
	}

	/** Return data contained at a given Uri as Base64. Defaults to null. */
	public static String getDataFromURI(
			final ContentResolver contentResolver,
			final Uri uri) {
		try {
			final InputStream inputStream = contentResolver.openInputStream(uri);
			final byte[] bytes = ByteStreams.toByteArray(inputStream);
			return Base64.encodeToString(bytes, Base64.NO_WRAP);
		}
		catch (IOException e) {
			return "";
		}
	}

	/** Convert the Uri to the direct file system path of the image file.
	*
	* source: https://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework/20402190?noredirect=1#comment30507493_20402190 */
	public static String getRealPathFromURI(
			final ContentResolver contentResolver,
			final Uri uri) {
		final String[] proj = { MediaStore.Images.Media.DATA };
		final Cursor cursor = contentResolver.query(uri, proj, null, null, null);
		if (cursor == null) {
			return "";
		}
		final int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
		if (column_index < 0) {
			cursor.close();
			return "";
		}
		cursor.moveToFirst();
		final String result = cursor.getString(column_index);
		cursor.close();
		return result;
	}
}
// vim: ts=4:sw=4:et
