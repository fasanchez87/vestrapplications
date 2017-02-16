package com.ingeniapps.vestra.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.fastaccess.permission.base.PermissionHelper;
import com.google.android.gms.iid.InstanceID;
import com.google.firebase.messaging.FirebaseMessaging;
import com.ingeniapps.vestra.R;
import com.ingeniapps.vestra.adapter.EndlessRecyclerViewScrollListener;
import com.ingeniapps.vestra.adapter.EventoAdapter;
import com.ingeniapps.vestra.app.Config;
import com.ingeniapps.vestra.beans.Evento;
import com.ingeniapps.vestra.sharedPreferences.gestionSharedPreferences;
import com.ingeniapps.vestra.util.NotificationUtils;
import com.ingeniapps.vestra.util.UtilDireccionIP;
import com.ingeniapps.vestra.vars.vars;
import com.ingeniapps.vestra.volley.ControllerSingleton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Inicio extends AppCompatActivity
{

    private String TAG = this.getClass().getSimpleName();

    private com.ingeniapps.vestra.vars.vars vars;
    private String telephonyManagerDevice, telephonyManagerSerial, telephonyManagerAndroidId;
    private TelephonyManager telephonyManager;

    private boolean solicitando=false;

    public SwitchCompat switch_notificaciones_eventos;

    //FCM
    private BroadcastReceiver mRegistrationBroadcastReceiver;

    private String modelDevice;

    RequestQueue mRequestQueue;

    Context context;

    gestionSharedPreferences sharedPreferences;

    ControllerSingleton controllerSingleton;

    private boolean loading = true;

    //PERMISSIONS RUNTIME ANDROID >6.0
    private String messageAlert = "";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int PERMISSION_REQUEST_CODE_DEVICE = 2;
    private TextView result;
    private PermissionHelper permissionHelper;
    private boolean isSingle=true;
    private android.support.v7.app.AlertDialog builder;
    private String[] neededPermission;

    public UtilDireccionIP gestionRed;


   private  boolean shouldExecuteOnResume=false;

    private SharedPreferences pref;

    public String currentVersion = null;

    private MenuItem menuConfiguracion;

    private final static String SINGLE_PERMISSION = Manifest.permission.READ_PHONE_STATE;

    private final static String[] MULTI_PERMISSIONS = new String[]
            {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE
            };

    String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE};
    public static final int MULTIPLE_PERMISSIONS = 2; // code you want.


    private EventoAdapter mAdapter;
    private RecyclerView recyclerView;
    LinearLayoutManager mLayoutManager;
    private ArrayList<Evento> eventos;
    private ArrayList<Evento> temp;
    private ProgressDialog progressDialog;

    private String _urlWebService = "http://fasttrackcenter.com/feeds.json";

    private String tokenFCM, idDevice;

    private int pagina;

    Dialog dialog;


    private EndlessRecyclerViewScrollListener scrollListener;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inicio);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        sharedPreferences = new gestionSharedPreferences(this);

        shouldExecuteOnResume = false;

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        sharedPreferences.putString("idDevice", InstanceID.getInstance(getApplicationContext()).getId());//ID UNICO DE DISPOSITIVO

        SharedPreferences pref = getApplicationContext().getSharedPreferences(Config.SHARED_PREF, 0);
        tokenFCM = pref.getString("regId", null);//TOKEN FCM

        vars = new vars();


        eventos = new ArrayList<>();

        context = this;
        pagina=0;

        try
        {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mLayoutManager = new LinearLayoutManager(this);

        mAdapter = new EventoAdapter(this,eventos);

        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(mLayoutManager)
        {
            @Override
            public void onLoadMore(final int page, final int totalItemsCount, final RecyclerView view)
            {
                final int curSize = mAdapter.getItemCount();

                view.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if(!solicitando)
                        {
                            // Notify adapter with appropriate notify methods
                            _webServiceGetEventosMore();
                        }
                    }
                });
            }
        };

        recyclerView.addOnScrollListener(scrollListener);

        mRegistrationBroadcastReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                // checking for type intent filter
                if (intent.getAction().equals(Config.REGISTRATION_COMPLETE))
                {
                    // gcm successfully registered
                    // now subscribe to `global` topic to receive app wide notifications
                    FirebaseMessaging.getInstance().subscribeToTopic(Config.TOPIC_GLOBAL);

                    //displayFirebaseRegId();

                }

                else

                if (intent.getAction().equals(Config.PUSH_NOTIFICATION))
                {
                    // new push notification is received

                    String message = intent.getStringExtra("message");

                    Toast.makeText(getApplicationContext(), "Nuevo evento publicado!", Toast.LENGTH_LONG).show();

                    //txtMessage.setText(message);
                }
            }
        };

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);

        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(this, recyclerView, new ClickListener()
        {
            @Override
            public void onClick(View view, int position)
            {
            }

            @Override
            public void onLongClick(View view, int position)
            {
            }
        }));


        if(!sharedPreferences.getBoolean("deviceRegistrado"))
        {
            serviceRegistroDispositivo();
        }

        else
        {
            _webServiceGetEventos();
        }

        _webServicecheckVersionAppPlayStore();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        menuConfiguracion.setVisible(true);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_inicio, menu);
        menuConfiguracion = (MenuItem) menu.findItem(R.id.action_configuracion_inicio);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.action_configuracion_inicio:
                Intent intent = new Intent(Inicio.this, Configuracion.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static int compareVersions(String version1, String version2)//COMPARAR VERSIONES
    {
        String[] levels1 = version1.split("\\.");
        String[] levels2 = version2.split("\\.");

        int length = Math.max(levels1.length, levels2.length);
        for (int i = 0; i < length; i++){
            Integer v1 = i < levels1.length ? Integer.parseInt(levels1[i]) : 0;
            Integer v2 = i < levels2.length ? Integer.parseInt(levels2[i]) : 0;
            int compare = v1.compareTo(v2);
            if (compare != 0){
                return compare;
            }
        }
        return 0;
    }

    private void _webServicecheckVersionAppPlayStore()
    {
        _urlWebService = "http://carreto.pt/tools/android-store-version/?package=com.ingeniapps.vestra";

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET, _urlWebService, null,
                new Response.Listener<JSONObject>()
                {
                    @Override
                    public void onResponse(JSONObject response)
                    {
                        try
                        {
                            boolean status = response.getBoolean("status");

                            if(status)
                            {
                                if(compareVersions(currentVersion,response.getString("version")) == -1)
                                {
                                    if(!((Activity) context).isFinishing())
                                    {
                                        //show dialog
                                        dialog = new Dialog(Inicio.this);
                                        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                                        dialog.setCancelable(false);
                                        dialog.setContentView(R.layout.custom_dialog);

                                        TextView text = (TextView) dialog.findViewById(R.id.text_dialog);
                                        //text.setText(msg);

                                        Button dialogButton = (Button) dialog.findViewById(R.id.btn_dialog);
                                        dialogButton.setOnClickListener(new View.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(View v)
                                            {
                                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                                intent.setData(Uri.parse("market://details?id=com.ingeniapps.vestra"));
                                                startActivity(intent);
                                            }
                                        });

                                        dialog.show();
                                    }
                               }
                            }
                        }

                        catch (JSONException e)
                        {
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setTitle("ERROR")
                                    .setMessage("Error consultando versiones en Play Store, contacte al admin de Beya.")
                                    .setPositiveButton("ACEPTAR", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {

                                        }
                                    }).setCancelable(true).show();

                            e.printStackTrace();
                        }
                    }
                },

                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                    }
                })

        {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError
            {
                HashMap<String, String> headers = new HashMap <String, String>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("WWW-Authenticate", "xBasic realm=".concat(""));
                return headers;
            }

        };

        ControllerSingleton.getInstance().addToReqQueue(jsonObjReq, "");
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(20000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegistrationBroadcastReceiver);
        Log.i("onPause","onPause");
        if(sharedPreferences.getBoolean("deviceRegistrado"))
        {
            finish();
            ControllerSingleton.getInstance().getReqQueue().cancelAll(TAG);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // register GCM registration complete receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                new IntentFilter(Config.REGISTRATION_COMPLETE));
        // register new push message receiver
        // by doing this, the activity will be notified each time a new message arrives
        LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                new IntentFilter(Config.PUSH_NOTIFICATION));
        // clear the notification area when the app is opened
        NotificationUtils.clearNotifications(getApplicationContext());

        //updateTokenFCMToServer(tokenFCM);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        Log.i("destroy","destroy");
        //ControllerSingleton.getInstance().getReqQueue().cancelAll("RequestRegistroDevice");
    }

    private void _webServiceGetEventos()
    {
        eventos.clear();

        _urlWebService = vars.ipServer.concat("/ws/getLastItems");

        progressDialog = new ProgressDialog(Inicio.this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage("Cargando eventos, espera un momento...");
        progressDialog.show();
        progressDialog.setCancelable(false);

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST, _urlWebService, null,
                new Response.Listener<JSONObject>()
                {
                    @Override
                    public void onResponse(JSONObject response)
                    {
                        try
                        {
                            JSONArray listaEventosWS = response.getJSONArray("eventos");

                            for (int i = 0; i < listaEventosWS.length(); i++)
                            {
                                JSONObject jsonObject = (JSONObject) listaEventosWS.get(i);

                                Evento evento = new Evento();

                                //AGREGAMOS PARA PONER EL PROGRESS AL FINAL DE PANTALLA
                                evento.setType(jsonObject.getString("type"));//type==evento
                                //DATOS EVENTO
                                evento.setCodEvento(jsonObject.getString("codEvento"));
                                evento.setCodEmpresa(jsonObject.getString("codEmpresa"));
                                evento.setFecEvento(jsonObject.getString("fecEvento"));
                                evento.setTimeStampItemEvento(jsonObject.getString("timeStampItemEvento"));
                                evento.setNomEvento(jsonObject.getString("nomEvento"));
                                evento.setDetEvento(jsonObject.getString("detEvento"));
                                evento.setDesEvento(jsonObject.getString("desEvento"));
                                evento.setCodCiudad(jsonObject.getString("codCiudad"));
                                evento.setNomCiudad(jsonObject.getString("nomCiudad")+" - ");
                                //MULTIMEDIA DEL EVENTO///////////////////////////////////
                                String urlVideo = TextUtils.isEmpty(jsonObject.getString("vidEvento")) ? null : jsonObject
                                        .getString("vidEvento");
                                evento.setVidEvento(urlVideo);
                                String urlImagen = TextUtils.isEmpty(jsonObject.getString("imgEvento")) ? null : jsonObject
                                        .getString("imgEvento");
                                evento.setImgEvento(urlImagen);
                                //////////////////////////////////////////////////////////
                                evento.setGpsEvento(jsonObject.getString("gpsEvento"));
                                evento.setIndPublicar(jsonObject.getString("indPublicar"));
                                evento.setFecPublicado(jsonObject.getString("fecPublicado"));
                                evento.setUsrPublicado(jsonObject.getString("usrPublicado"));
                                evento.setUsrCreacion(jsonObject.getString("usrCreacion"));
                                evento.setFecCreacion(jsonObject.getString("fecCreacion"));
                                evento.setUsrEdicion(jsonObject.getString("usrEdicion"));
                                evento.setFecEdicion(jsonObject.getString("fecEdicion"));
                                //CARGAMOS PREFERECIA DE ME INTERESA DEL EVENTO
                                evento.setMeInteresa(jsonObject.getString("meInteresa"));

                                eventos.add(evento);
                            }

                            pagina+=1;

                        }
                        catch (JSONException e)
                        {

                            progressDialog.dismiss();

                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage(e.getMessage().toString())
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();

                            e.printStackTrace();
                        }
                        mAdapter.notifyDataSetChanged();
                        progressDialog.dismiss();
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        if (error instanceof TimeoutError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de conexión, sin respuesta del servidor.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof NoConnectionError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Por favor, conectese a la red.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof AuthFailureError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de autentificación en la red, favor contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof ServerError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error server, sin respuesta del servidor.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof NetworkError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de red, contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof ParseError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de conversión Parser, contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }
                    }
                })
        {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError
            {
                HashMap<String, String> headers = new HashMap <String, String>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("WWW-Authenticate", "xBasic realm=".concat(""));
                headers.put("MyToken", sharedPreferences.getString("MyTokenAPI"));
                headers.put("numIndex", String.valueOf(0));
                headers.put("idDevice", sharedPreferences.getString("idDevice"));
                headers.put("tokenFCM",tokenFCM);
                headers.put("numVersion",currentVersion);
                return headers;
            }
        };

        ControllerSingleton.getInstance().addToReqQueue(jsonObjReq, TAG);
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(20000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    }

    private void _webServiceGetEventosMore()
    {
        solicitando=true;//PARA ESPERAR A QUE CARGUEN LOS DEMAS ITEMSY LOGRAR OCULTAR EL PROGRESS.

        eventos.add(new Evento("load"));
        mAdapter.notifyItemInserted(eventos.size()-1);

        _urlWebService = vars.ipServer.concat("/ws/getLastItems");

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST, _urlWebService, null,
                new Response.Listener<JSONObject>()
                {
                    @Override
                    public void onResponse(JSONObject response)
                    {
                        try
                        {
                            eventos.remove(eventos.size()-1);

                            if(response.getJSONArray("eventos").length()>0)
                            {
                                JSONArray listaEventosWS = response.getJSONArray("eventos");

                                for (int i = 0; i < listaEventosWS.length(); i++)
                                {
                                    JSONObject jsonObject = (JSONObject) listaEventosWS.get(i);

                                    Evento evento = new Evento();

                                    //AGREGAMOS PARA PONER EL PROGRESS AL FINAL DE PANTALLA
                                    evento.setType(jsonObject.getString("type"));//type==evento
                                    //DATOS EVENTO
                                    evento.setCodEvento(jsonObject.getString("codEvento"));
                                    evento.setCodEmpresa(jsonObject.getString("codEmpresa"));
                                    evento.setFecEvento(jsonObject.getString("fecEvento"));
                                    evento.setTimeStampItemEvento(jsonObject.getString("timeStampItemEvento"));
                                    evento.setNomEvento(jsonObject.getString("nomEvento"));
                                    evento.setDetEvento(jsonObject.getString("detEvento"));
                                    evento.setDesEvento(jsonObject.getString("desEvento"));
                                    evento.setCodCiudad(jsonObject.getString("codCiudad"));
                                    evento.setNomCiudad(jsonObject.getString("nomCiudad")+" - ");
                                    //MULTIMEDIA DEL EVENTO///////////////////////////////////
                                    String urlVideo = TextUtils.isEmpty(jsonObject.getString("vidEvento")) ? null : jsonObject
                                            .getString("vidEvento");
                                    evento.setVidEvento(urlVideo);
                                    String urlImagen = TextUtils.isEmpty(jsonObject.getString("imgEvento")) ? null : jsonObject
                                            .getString("imgEvento");
                                    evento.setImgEvento(urlImagen);
                                    //////////////////////////////////////////////////////////
                                    evento.setGpsEvento(jsonObject.getString("gpsEvento"));
                                    evento.setIndPublicar(jsonObject.getString("indPublicar"));
                                    evento.setFecPublicado(jsonObject.getString("fecPublicado"));
                                    evento.setUsrPublicado(jsonObject.getString("usrPublicado"));
                                    evento.setUsrCreacion(jsonObject.getString("usrCreacion"));
                                    evento.setFecCreacion(jsonObject.getString("fecCreacion"));
                                    evento.setUsrEdicion(jsonObject.getString("usrEdicion"));
                                    evento.setFecEdicion(jsonObject.getString("fecEdicion"));
                                    evento.setMeInteresa(jsonObject.getString("meInteresa"));

                                    eventos.add(evento);
                                }

                                pagina+=1;
                            }
                            else
                            {//result size 0 means there is no more data available at server
                                //mAdapter.notifyItemRangeInserted(1, eventos.size());
                                mAdapter.setMoreDataAvailable(false);
                                //scrollListener.resetState();
                                //telling adapter to stop calling load more as no more server data available
/*
                                Toast.makeText(getApplicationContext(),"No More Data Available",Toast.LENGTH_LONG).show();
*/
                            }
                            mAdapter.notifyDataSetChanged();
                            solicitando=false;
                        }
                        catch (JSONException e)
                        {
                            progressDialog.dismiss();                            // buttonSeleccionarServicios.setVisibility(View.GONE);

                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage(e.getMessage().toString())
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();

                            e.printStackTrace();
                        }
                    }

                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        if (error instanceof TimeoutError)
                        {

                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de conexión, sin respuesta del servidor.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof NoConnectionError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Por favor, conectese a la red.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof AuthFailureError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de autentificación en la red, favor contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof ServerError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error server, sin respuesta del servidor.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof NetworkError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de red, contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof ParseError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de conversión Parser, contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }
                    }
                })
        {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError
            {
                HashMap<String, String> headers = new HashMap <String, String>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("WWW-Authenticate", "xBasic realm=".concat(""));
                headers.put("MyToken", sharedPreferences.getString("MyTokenAPI"));
                headers.put("numIndex", String.valueOf(pagina));
                headers.put("idDevice", sharedPreferences.getString("idDevice"));
                headers.put("tokenFCM",tokenFCM);
                headers.put("numVersion",currentVersion);
                return headers;
            }
        };

        ControllerSingleton.getInstance().addToReqQueue(jsonObjReq, TAG);
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(20000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    }

    private void serviceRegistroDispositivo()//REGISTRAMOS EL DEVICE SEGUN SU IMEI Y OTROS DATOS DEL TELEFONO
    {
        _urlWebService = vars.ipServer.concat("/ws/RegistroUsuario");

        progressDialog = new ProgressDialog(Inicio.this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage("Registrando dispositivo, espera un momento...");
        progressDialog.show();
        progressDialog.setCancelable(false);

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST, _urlWebService, null,
                new Response.Listener<JSONObject>()
                {
                    @Override
                    public void onResponse(JSONObject response)
                    {
                        try
                        {
                            sharedPreferences.putString("MyTokenAPI",response.getString("MyToken"));


                            if(response.getString("message").equals("success"))
                            {
                                sharedPreferences.putBoolean("deviceRegistrado",true);
                                progressDialog.dismiss();
                                _webServiceGetEventos();
                            }

                            progressDialog.dismiss();
                        }
                        catch (JSONException e)
                        {
                            e.printStackTrace();
                        }
                    }
                },

                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {

                        if (error instanceof TimeoutError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de conexión, sin respuesta del servidor.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof NoConnectionError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Por favor, conectese a la red.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof AuthFailureError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de autentificación en la red, favor contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof ServerError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error server, sin respuesta del servidor.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof NetworkError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de red, contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }

                        else

                        if (error instanceof ParseError)
                        {
                            progressDialog.dismiss();
                            AlertDialog.Builder builder = new AlertDialog.Builder(Inicio.this);
                            builder
                                    .setMessage("Error de conversión Parser, contacte a su proveedor de servicios.")
                                    .setPositiveButton("Aceptar", new DialogInterface.OnClickListener()
                                    {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id)
                                        {
                                        }
                                    }).show();
                        }
                    }
                })
        {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError
            {
                HashMap<String, String> headers = new HashMap <String, String>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("WWW-Authenticate", "xBasic realm=".concat(""));
                headers.put("idDevice", sharedPreferences.getString("idDevice"));//ID UNICO DE DISPOSITIVO
                headers.put("tokenFCM",tokenFCM);
                headers.put("numVersion",currentVersion);
                return headers;
            }
        };
        ControllerSingleton.getInstance().addToReqQueue(jsonObjReq, TAG);
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(20000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    }

    private void updateTokenFCMToServer(final String refreshedToken)
    {
        String _urlWebServiceUpdateToken = vars.ipServer.concat("/ws/UpdateTokenFCM");

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.POST, _urlWebServiceUpdateToken, null,
                new Response.Listener<JSONObject>()
                {
                    @Override
                    public void onResponse(JSONObject response)
                    {
                        try
                        {
                            Boolean status = response.getBoolean("status");
                            String message = response.getString("message");

                            if(status)
                            {
                            }

                            else
                            {
                            }
                        }
                        catch (JSONException e)
                        {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                    }
                })
        {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError
            {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "application/json; charset=utf-8");
                headers.put("WWW-Authenticate", "xBasic realm=".concat(""));
                headers.put("idDevice", sharedPreferences.getString("idDevice"));//ID UNICO DE DISPOSITIVO
                headers.put("tokenFCM",refreshedToken);
               // headers.put("MyToken", sharedPreferences.getString("MyTokenAPI"));
                return headers;
            }
        };
        ControllerSingleton.getInstance().addToReqQueue(jsonObjReq, TAG);
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(20000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    }

    public interface ClickListener
    {
        void onClick(View view, int position);

        void onLongClick(View view, int position);
    }

    public static class RecyclerTouchListener implements RecyclerView.OnItemTouchListener
    {
        private GestureDetector gestureDetector;
        private Inicio.ClickListener clickListener;

        public RecyclerTouchListener(Context context, final RecyclerView recyclerView, final Inicio.ClickListener clickListener)
        {
            this.clickListener = clickListener;
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener()
            {
                @Override
                public boolean onSingleTapUp(MotionEvent e)
                {
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e)
                {
                    View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (child != null && clickListener != null)
                    {
                        clickListener.onLongClick(child, recyclerView.getChildPosition(child));
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e)
        {
            View child = rv.findChildViewUnder(e.getX(), e.getY());
            if (child != null && clickListener != null && gestureDetector.onTouchEvent(e)) {
                clickListener.onClick(child, rv.getChildPosition(child));
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView rv, MotionEvent e)
        {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept)
        {
        }
    }

}


