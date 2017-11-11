package com.app.photosapp;

import android.*;
import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.ByteArrayOutputStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Created by Ankita Deshmukh on 06-11-2017.
 */

public class UploadImage_Activity extends Activity
        implements EasyPermissions.PermissionCallbacks, GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private Bitmap mBitmapToSave;

    com.google.api.services.drive.Drive mService = null;

    int PERMISSION_ALL = 1;
    ProgressDialog mProgress;
    ImageView img_one;
    static final int REQUEST_IMAGE_CAPTURE = 1;
    String mCurrentPhotoPath;
    private Uri FinalUri = null;
    java.io.File imageFile;
    public static int CHOOSE_FROM_GALLERY = 101;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;


    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {DriveScopes.DRIVE, "https://www.googleapis.com/auth/userinfo.profile", DriveScopes.DRIVE_METADATA};
    GoogleAccountCredential mCredential;
    Button btn_upload;
    private Uri outputUri = null;
    Long tsLong;
    String[] PERMISSIONS = {android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.CAMERA};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        initialize();

    }


    public static boolean hasPermissions(Context context, String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void dispatchTakePictureIntent() {
        java.io.File folder = new java.io.File(Environment.getExternalStorageDirectory() + "/PhotoApp");
        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdir();
        }
        if (success) {
            Long tsLong = System.currentTimeMillis() / 1000;
            String ts = tsLong.toString();
            mCurrentPhotoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/PhotoApp/" + ts + ".jpg";
            java.io.File imageFile = new java.io.File(mCurrentPhotoPath);
            outputUri = Uri.fromFile(imageFile); // convert path to Uri

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
            } else {
                java.io.File file = new java.io.File(outputUri.getPath());
                Uri photoUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            String errorMessage = "Whoops - your device doesn't support capturing images!";
            Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.img_one:
                showPictureDialog();
                break;

            case R.id.btn_upload:

                if (imageFile != null)
                    getResultsFromApi();
                else
                    Toast.makeText(getApplicationContext(), "Please select image to upload", Toast.LENGTH_SHORT).show();
                break;


        }
    }


    private void initialize() {


        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Uploading Image to Google Drive...");


        img_one = (ImageView) findViewById(R.id.img_one);
        img_one.setOnClickListener(this);

        btn_upload = (Button) findViewById(R.id.btn_upload);
        btn_upload.setOnClickListener(this);
    }


    @Override
    protected void
    onActivityResult(int requestCode, int resultCode, Intent data) {
        mBitmapToSave = null;
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {

            imageFile = new java.io.File(mCurrentPhotoPath);

            Bitmap bmp = null;
            Matrix mat = null;
            FinalUri = Uri.fromFile(imageFile);

            try {

                FinalUri = Uri.fromFile(imageFile);

                try {
                    ExifInterface exif = new ExifInterface(imageFile.getPath());
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    int angle = 0;
                    if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                        angle = 90;
                    } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                        angle = 180;
                    } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                        angle = 270;
                    }
                    mat = new Matrix();
                    mat.postRotate(angle);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    bmp = BitmapFactory.decodeStream(new FileInputStream(imageFile), null, options);
                } catch (IOException e) {
                    Log.w("TAG", "-- Error in setting image");
                } catch (OutOfMemoryError oom) {
                    Log.w("TAG", "-- OOM Error in setting image");
                }


            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                mBitmapToSave = MediaStore.Images.Media.getBitmap(getContentResolver(), FinalUri);
            } catch (IOException e) {
                e.printStackTrace();
            }


            mBitmapToSave = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);
            ByteArrayOutputStream outstudentstreamOutputStream = new ByteArrayOutputStream();
            mBitmapToSave.compress(Bitmap.CompressFormat.JPEG, 100, outstudentstreamOutputStream);

            img_one.setImageBitmap(mBitmapToSave);


        } else if (requestCode == CHOOSE_FROM_GALLERY && resultCode == Activity.RESULT_OK) {


            Bitmap bmp = null;
            Matrix mat = null;
            if (data != null) {
                outputUri = data.getData();

                if (outputUri != null) {
                    imageFile = new java.io.File(getRealPathFromURI(outputUri));
                    FinalUri = Uri.fromFile(imageFile);

                    try {
                        ExifInterface exif = new ExifInterface(imageFile.getPath());
                        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        int angle = 0;
                        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                            angle = 90;
                        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                            angle = 180;
                        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                            angle = 270;
                        }
                        mat = new Matrix();
                        mat.postRotate(angle);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2;
                        bmp = BitmapFactory.decodeStream(new FileInputStream(imageFile), null, options);
                    } catch (IOException e) {
                        Log.w("TAG", "-- Error in setting image");
                    } catch (OutOfMemoryError oom) {
                        Log.w("TAG", "-- OOM Error in setting image");
                    }


                    try {
                        mBitmapToSave = MediaStore.Images.Media.getBitmap(getContentResolver(), FinalUri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    mBitmapToSave = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);
                    ByteArrayOutputStream outstudentstreamOutputStream = new ByteArrayOutputStream();
                    mBitmapToSave.compress(Bitmap.CompressFormat.JPEG, 100, outstudentstreamOutputStream);

                    img_one.setImageBitmap(mBitmapToSave);

                }

            }

        } else {
            switch (requestCode) {
                case REQUEST_GOOGLE_PLAY_SERVICES:
                    if (resultCode != RESULT_OK) {
                        Log.e("!!!!!!!", "This app requires Google Play Services. Please install " +
                                "Google Play Services on your device and relaunch this app.");


                    } else {
                        getResultsFromApi();
                    }
                    break;
                case REQUEST_ACCOUNT_PICKER:
                    if (resultCode == RESULT_OK && data != null &&
                            data.getExtras() != null) {
                        String accountName =
                                data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                        if (accountName != null) {
                            SharedPreferences settings =
                                    getPreferences(Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putString(PREF_ACCOUNT_NAME, accountName);
                            editor.apply();
                            mCredential.setSelectedAccountName(accountName);
                            getResultsFromApi();
                        }
                    }
                    break;
                case REQUEST_AUTHORIZATION:
                    if (resultCode == RESULT_OK) {
                        getResultsFromApi();
                    }
                    break;
            }
        }


    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS, Manifest.permission.GET_ACCOUNTS
            );
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }


    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }


    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }


    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }


    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }


    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                UploadImage_Activity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            //   mOutputText.setText("No network connection available.");
            Log.e("BBBBBBBB", "No network connection available.");
        } else {

            uploadFile(mCredential);
        }

    }

    private void uploadFile(GoogleAccountCredential credential) {


        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mService = new com.google.api.services.drive.Drive.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("PhotosApp")
                .build();

        tsLong = System.currentTimeMillis() / 1000;


        AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mProgress.show();
            }

            @Override
            protected String doInBackground(Void... params) {
                File fileMetadata = new File();

                fileMetadata.setName(tsLong.toString() + "_photo.jpg");

                FileContent mediaContent = new FileContent("image/jpeg", imageFile);
                File file = null;
                try {
                    file = mService.files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute();

                } catch (
                        UserRecoverableAuthIOException e)

                {
                    startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                } catch (
                        IOException e)

                {
                    e.printStackTrace();
                }

                if (file != null && file.getId() != null && !file.getId().

                        equalsIgnoreCase(""))

                {
                    return file.getId();
                } else
                    return "";

            }

            @Override
            protected void onPostExecute(String token) {

                mProgress.dismiss();
                if (token.equalsIgnoreCase("")) {
                    Toast.makeText(UploadImage_Activity.this, "Sorry Please try again", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(UploadImage_Activity.this, "Image " + tsLong.toString() + "_photo.jpg Uploaded Successfully", Toast.LENGTH_LONG).show();

                }


            }

        };
        task.execute();
    }

    private void choosePhotoFromGallery() {
        Intent i = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, CHOOSE_FROM_GALLERY);

    }

    private void showPictureDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(getString(R.string.text_choosepicture));
        String[] items = {getString(R.string.text_gallary),
                getString(R.string.text_camera)};

        dialog.setItems(items, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // TODO Auto-generated method stub
                switch (which) {
                    case 0:
                        choosePhotoFromGallery();
                        break;
                    case 1:
                        dispatchTakePictureIntent();
                        break;

                }
            }
        });
        dialog.show();
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }


}
