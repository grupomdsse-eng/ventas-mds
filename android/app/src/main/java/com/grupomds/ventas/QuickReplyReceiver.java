package com.grupomds.ventas;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.RemoteInput;

/** Envía la respuesta escrita desde la notificación sin abrir el chat. */
public class QuickReplyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Bundle results=RemoteInput.getResultsFromIntent(intent);CharSequence reply=results==null?null:results.getCharSequence(MdsMessagingService.KEY_REPLY);if(reply==null||reply.toString().trim().isEmpty())return;
        PendingResult pending=goAsync();int recipient=parse(intent.getStringExtra("recipient_id")),group=parse(intent.getStringExtra("group_id")),notificationId=intent.getIntExtra("notification_id",0);String body=reply.toString().trim(),messageId="native-"+System.currentTimeMillis()+"-"+Math.abs((body+recipient+group).hashCode());
        new Thread(()->{try{if(WidgetApi.quickReply(recipient,group,body,messageId)){NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null)manager.cancel(notificationId);}}catch(Exception ignored){}finally{pending.finish();}}).start();
    }
    private int parse(String value){try{return Integer.parseInt(value==null?"0":value);}catch(NumberFormatException error){return 0;}}
}
