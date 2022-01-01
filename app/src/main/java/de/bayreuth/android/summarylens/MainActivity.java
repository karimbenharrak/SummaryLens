package de.bayreuth.android.summarylens;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_GALLERY = 0;
    private static final int REQUEST_CAMERA = 1;
    private static final int MY_PERMISSIONS_REQUESTS = 0;

    private static final String TAG = MainActivity.class.getSimpleName();

    private Uri imageUri;
    private TextView detectedTextView;
    private String detectedText;
    private String summarizedText;
    private Switch aSwitch;
    private boolean isReading = false;

    TextToSpeech t1;
    private boolean t1IsInitialized = false;

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUESTS: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    // FIXME: Handle this case the user denied to grant the permissions
                }
                break;
            }
            default:
                // TODO: Take care of this case later
                break;
        }
    }

    private void requestPermissions()
    {
        List<String> requiredPermissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA);
        }

        if (!requiredPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    requiredPermissions.toArray(new String[]{}),
                    MY_PERMISSIONS_REQUESTS);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button readButton = findViewById(R.id.readout);

        requestPermissions();

        findViewById(R.id.choose_from_gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, REQUEST_GALLERY);
            }
        });

        findViewById(R.id.take_a_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String filename = System.currentTimeMillis() + ".jpg";

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                Intent intent = new Intent();
                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(intent, REQUEST_CAMERA);
            }
        });

        findViewById(R.id.readout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isReading) {
                    String toSpeak = detectedTextView.getText().toString();
                    t1.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
                    readButton.setText("STOP SPEAKING");
                    isReading = true;
                } else {
                    isReading = false;
                    t1.stop();
                    readButton.setText("READ OUT LOUD");
                }
            }
        });

        /*findViewById(R.id.summarize).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                summarize(detectedText);
                detectedTextView.setText(summarizedText);
            }
        });*/

        aSwitch = findViewById(R.id.switch1);

        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // do something, the isChecked will be
                // true if the switch is in the On position
                if(detectedTextView.getText().length() > 0){
                    if (!isChecked) {
                        detectedTextView.setText(Html.fromHtml("<font color='#009f7a'>" + summarizedText + "</font>"));
                        TextView tw = (TextView) findViewById(R.id.textView2);
                        tw.setTextColor(Color.rgb(0, 159, 122));
                        tw = (TextView) findViewById(R.id.textView);
                        tw.setTextColor(Color.BLACK);
                    } else {

                        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
                        iterator.setText(summarizedText);
                        int start = iterator.first();
                        for (int end = iterator.next();
                             end != BreakIterator.DONE;
                             start = end, end = iterator.next()) {
                            System.out.println(summarizedText.substring(start, end));
                            detectedText = detectedText.replaceAll(summarizedText.substring(start, end), "<font color='#009f7a'>" + summarizedText.substring(start, end) + "</font>");
                        }
                        detectedTextView.setText(Html.fromHtml(detectedText));
                        TextView tw = (TextView) findViewById(R.id.textView);
                        tw.setTextColor(Color.rgb(0, 159, 122));
                        tw = (TextView) findViewById(R.id.textView2);
                        tw.setTextColor(Color.BLACK);
                    }
                }
            }
        });

        detectedTextView = (TextView) findViewById(R.id.detected_text);
        detectedTextView.setMovementMethod(new ScrollingMovementMethod());

        t1 = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS){
                    int result = t1.setLanguage(Locale.ENGLISH);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                        Log.e("TTS", "Language not supported");
                    } else {
                        t1IsInitialized = true; // flag tts as initialized
                    }
                } else {
                    Log.e("TTS", "Failed");
                }
            }
        });
    }

    private void summarize(String input){

        ArrayList<String> finalText = new ArrayList<>();
        String par = "";
        for(char i : input.toCharArray()){
            if (i != '\n') par += i;
            else {
                //finalText.add(par);
                //par = "";
                par += " ";
            }
        }
        finalText.add(par);
        for(String i : finalText)
            System.out.println(i);

        // Simulate
        //summarizedText = "A few people were uncertain; for example, they thought the size of apartments might reduce as the population of the city grew, but they also thought that public transport would become better. In general, they believed that the cities of the future would offer a much more convenient way of living. A smaller number thought cities might become a lot cleaner, and might be built from more interesting materials. In many countries around the world, people are choosing to have smaller families or to wait longer before they start a family. In the past, the architects who were responsible for planning our cities often designed buildings that they were interested in; but now it is time for them to listen carefully to what people living in cities are asking for.";
        //detectedTextView.setText(Html.fromHtml("<font color='#009f7a'>" + summarizedText + "</font>"));



        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
        String url = "https://btn6xd.inf.uni-bayreuth.de/summarization_api/summarize-text2scan";


        JSONObject jsonBody = null;
        try {
            jsonBody = new JSONObject("{\"eingabe\":\"" + removeDoubleQuotes(par) + "\"}");
        } catch (JSONException e) {
            e.printStackTrace();
        }


        JsonObjectRequest jsonArrayRequest = new JsonObjectRequest(Request.Method.POST, url, jsonBody, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject s) {
                try {
                    System.out.println(s.getString("result"));
                    summarizedText = s.getString("result");
                    detectedTextView.setText(Html.fromHtml("<font color='#009f7a'>" + summarizedText + "</font>"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                System.out.println("Response Failed: " + volleyError.getMessage());
                summarizedText = "Service offline!";
                detectedTextView.setText(Html.fromHtml("<font color='#009f7a'>" + summarizedText + "</font>"));
            }
        }){
                    /*@Override
                    protected Map<String, String> getParams(){
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("eingabe", "finalPar");
                        return params;
                    }*/

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Content-Type", "application/json");
                return params;
            }
        };

        queue.add(jsonArrayRequest);
    }

    public static String removeDoubleQuotes(String input){

        StringBuilder sb = new StringBuilder();

        char[] tab = input.toCharArray();
        for( char current : tab ){
            if( current != '"' )
                sb.append( current );
        }

        return sb.toString();
    }

    private void inspectFromBitmap(Bitmap bitmap) {
        TextRecognizer textRecognizer = new TextRecognizer.Builder(this).build();
        try {

            detectedTextView.setText("");
            detectedText = "";
            summarizedText = "";

            aSwitch.setChecked(false);


            if (!textRecognizer.isOperational()) {
                new AlertDialog.
                        Builder(this).
                        setMessage("Text recognizer could not be set up on your device").show();
                return;
            }

            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            SparseArray<TextBlock> origTextBlocks = textRecognizer.detect(frame);
            List<TextBlock> textBlocks = new ArrayList<>();
            for (int i = 0; i < origTextBlocks.size(); i++) {
                TextBlock textBlock = origTextBlocks.valueAt(i);
                textBlocks.add(textBlock);
            }
            Collections.sort(textBlocks, new Comparator<TextBlock>() {
                @Override
                public int compare(TextBlock o1, TextBlock o2) {
                    int diffOfTops = o1.getBoundingBox().top - o2.getBoundingBox().top;
                    int diffOfLefts = o1.getBoundingBox().left - o2.getBoundingBox().left;
                    if (diffOfTops != 0) {
                        return diffOfTops;
                    }
                    return diffOfLefts;
                }
            });

            StringBuilder detectedText = new StringBuilder();
            for (TextBlock textBlock : textBlocks) {
                if (textBlock != null && textBlock.getValue() != null) {
                    detectedText.append(textBlock.getValue());
                    detectedText.append("\n");
                }
            }

            detectedTextView.setText(detectedText);

            // flatten text
            String testText = detectedTextView.getText().toString();
            detectedTextView.setText("");

            ArrayList<String> finalText = new ArrayList<>();
            String par = "";
            for(char i : testText.toCharArray()){
                if (i != '\n') par += i;
                else {
                    //finalText.add(par);
                    //par = "";
                    par += " ";
                }
            }
            finalText.add(par);
            for(String i : finalText)
                System.out.println(i);

            //Simulate
            // par = "Researchers recently gave 1,000 people a questionnaire about 'Cities of The Future'. To answer the questions, the people had to imagine and describe what they thought our cities might look like in the year 2050. Interestingly, a large number of people were anxious that they would become 'dark, dangerous places', which had endless traffic jams and very few green spaces. This group also predicted an increase in the level of pollution and thought people would always need to wear facemasks in order to breathe. They also believed that it would be less safe to walk on the street as there would be 'more stealing' and other criminal behaviour. A smaller number thought cities might become a lot cleaner, and might be built from more interesting materials. They were also looking forward to new technology such as fiying cars and moving pavements. In general, they believed that the cities of the future would offer a much more convenient way of living. A few people were uncertain; for example, they thought the size of apartments might reduce as the population of the city grew, but they also thought that public transport would become better What do the results from this questionnaire tell us? In a way, we shouldn't be amazed by the descriptions of the largest group. So many Hllywood films show cities of the future as frightening places. Online newspapers are also responsible for spreading this same belief. Headlines such as 'Global population rises - cities become crowded' are becoming more frequent. Journalists rarely discuss how future cities might be a good place to live. The facts are these: 50% of people now live in cities, even though cities only occupy 2% of the world's land. By 2050, it is predicted that the number of people living there will rise to 70%. Some people are worried that villages in the countryside will become empty as everyone leaves for the city, and so traditional ways of life will be lost. This may be true, but we have to accept changes like this as part of human development. Rather than being negative, we should be hopeful that we can improve people's lives as they move to cities. The way to do this is through intelligent planning. Architects have a big role to play in our future cities. In the past, the architects who were responsible for planning our cities often designed buildings that they were interested in; but now it is time for them to listen carefully to what people living in cities are asking for. In many countries around the world, people are choosing to have smaller families or to wait longer before they start a family. For this reason, not everyone needs a large house. Smaller and cheaper houses are what they need. But 'small' doesn't have to be the same as 'ugly' or 'boring'. Western architects could perhaps look at some of the architecture in Japanese cities, where very stylish houses are built on small pieces of unused land. And what might cities of the future be made from? Engineering companies have produced some interesting new products, for example, wood-like material made from recycled newspapers or old drink cartons. One engineering team are even working on a project that uses mushrooms to create a hard building material. These new materials may seem strange, but we should remember that plastic was only invented in 1907-at the time people thought that this was an unusual product, but now it is something we cannot manage without. Building a city of the future requires imagination and an open mind. </font>";

            this.detectedText = par;
            summarize(par);
            TextView tw = (TextView) findViewById(R.id.textView2);
            tw.setTextColor(Color.rgb(0, 159, 122));

        }
        finally {
            textRecognizer.release();
        }
    }

    private void inspect(Uri uri) {
        InputStream is = null;
        Bitmap bitmap = null;
        try {
            is = getContentResolver().openInputStream(uri);
            /*BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = 2;
            options.inScreenDensity = DisplayMetrics.DENSITY_LOW;
            bitmap = BitmapFactory.decodeStream(is, null, options);*/
            BufferedInputStream bufferedInputStream = new BufferedInputStream(is);
            Bitmap bitmap1 = BitmapFactory.decodeStream(bufferedInputStream);
            inspectFromBitmap(bitmap1);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Failed to find the file: " + uri, e);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close InputStream", e);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_GALLERY:
                if (resultCode == RESULT_OK) {
                    inspect(data.getData());
                }
                break;
            case REQUEST_CAMERA:
                if (resultCode == RESULT_OK) {
                    if (imageUri != null) {
                        inspect(imageUri);
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}