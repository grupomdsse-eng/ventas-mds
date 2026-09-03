package com.grupomds.ventas;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.capacitorjs.plugins.pushnotifications.MessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Notificaciones agrupadas por conversación, con respuesta rápida nativa. */
public class MdsMessagingService extends MessagingService {
    static final String ACTION_REPLY="com.grupomds.ventas.REPLY";
    static final String KEY_REPLY="mds_quick_reply";
    private static final String CHAT_CHANNEL="mds_chat";
    private static final String ORDER_CHANNEL="mds_orders";

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String,String> data=message.getData();
        if ("orders".equals(data.get("widget_refresh"))) {
            WidgetUpdater.refreshPendingWidgets(getApplicationContext());
        }
        if("chat".equals(data.get("kind"))) showChat(data);
        else if("order".equals(data.get("kind"))) showOrder(data);
    }

    private void channels(){if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return;NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel(CHAT_CHANNEL,"Mensajes de MDS Ventas",NotificationManager.IMPORTANCE_HIGH));manager.createNotificationChannel(new NotificationChannel(ORDER_CHANNEL,"Pedidos de MDS Ventas",NotificationManager.IMPORTANCE_HIGH));}
    private int flags(boolean mutable){int value=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)value|=mutable&&Build.VERSION.SDK_INT>=Build.VERSION_CODES.S?PendingIntent.FLAG_MUTABLE:PendingIntent.FLAG_IMMUTABLE;return value;}
    private PendingIntent contentIntent(Map<String,String> data,int id){Intent open=new Intent(this,MainActivity.class).putExtra(MainActivity.EXTRA_WIDGET_URL,data.getOrDefault("url",WidgetApi.BASE_URL+"/index.php?page=chat")).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);return PendingIntent.getActivity(this,id,open,flags(false));}
    private void showChat(Map<String,String> data){channels();String key=data.getOrDefault("conversation_key","chat"),title=data.getOrDefault("title","Nuevo mensaje"),body=data.getOrDefault("body","Tienes un nuevo mensaje."),url=data.getOrDefault("url",WidgetApi.BASE_URL+"/index.php?page=chat");int id=Math.abs(key.hashCode());Intent reply=new Intent(this,QuickReplyReceiver.class).setAction(ACTION_REPLY).putExtra("recipient_id",data.getOrDefault("with_id","0")).putExtra("group_id",data.getOrDefault("group_id","0")).putExtra("notification_id",id).putExtra(MainActivity.EXTRA_WIDGET_URL,url);PendingIntent replyIntent=PendingIntent.getBroadcast(this,id,reply,flags(true));androidx.core.app.RemoteInput input=new androidx.core.app.RemoteInput.Builder(KEY_REPLY).setLabel("Responder").build();NotificationCompat.Action action=new NotificationCompat.Action.Builder(R.drawable.ic_notification,"Responder",replyIntent).addRemoteInput(input).setAllowGeneratedReplies(true).build();NotificationCompat.Builder notification=new NotificationCompat.Builder(this,CHAT_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(contentIntent(data,id)).setAutoCancel(true).setOnlyAlertOnce(false).setCategory(NotificationCompat.CATEGORY_MESSAGE).setGroup("mds-conversation-"+key).addAction(action);NotificationManagerCompat.from(this).notify(id,notification.build());}
    private void showOrder(Map<String,String> data){channels();String title=data.getOrDefault("title","Nuevo pedido"),body=data.getOrDefault("body","Hay un pedido pendiente."),url=data.getOrDefault("url",WidgetApi.BASE_URL+"/index.php?page=orders");int id=Math.abs(("order-"+data.getOrDefault("order_id","0")).hashCode());Intent open=new Intent(this,MainActivity.class).putExtra(MainActivity.EXTRA_WIDGET_URL,url);NotificationCompat.Builder notification=new NotificationCompat.Builder(this,ORDER_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(PendingIntent.getActivity(this,id,open,flags(false))).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_STATUS);NotificationManagerCompat.from(this).notify(id,notification.build());}
}
