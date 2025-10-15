package com.example.wahook;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.ContentResolver;
import android.content.ContentProvider;
import android.net.Uri;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String ACTION = "com.example.wahook.WA_MESSAGE";
    private static final String DEFAULT_WEBHOOK = "https://in.swipe4eng.ru/webhook/50ccfe2e-76b2-410a-8359-7acf2e93ba56"; // fallback
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile String webhookUrl = DEFAULT_WEBHOOK; // ensure non-null early
    private final Map<Object, Object[]> stmtBindArgs = Collections.synchronizedMap(new WeakHashMap<Object, Object[]>());

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.whatsapp".equals(lpparam.packageName)) return;

        XposedBridge.log("WAHook: loaded for " + lpparam.packageName);
        // Resolve webhook URL from our module's manifest meta-data once
        try {
            final String modulePkg = "com.example.wahook";
            Context ctx0 = getApplicationContext();
            if (ctx0 != null) {
                PackageManager pm = ctx0.getPackageManager();
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(modulePkg, PackageManager.GET_META_DATA);
                    Bundle md = ai.metaData;
                    if (md != null) webhookUrl = md.getString("wahook.webhook");
                } catch (Throwable ignored) {}
                if (webhookUrl == null) {
                    try {
                        Context other = ctx0.createPackageContext(modulePkg, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
                        PackageManager pm2 = other.getPackageManager();
                        ApplicationInfo ai2 = pm2.getApplicationInfo(modulePkg, PackageManager.GET_META_DATA);
                        Bundle md2 = ai2.metaData;
                        if (md2 != null) webhookUrl = md2.getString("wahook.webhook");
                    } catch (Throwable ignored) {}
                }
                if (webhookUrl == null || webhookUrl.isEmpty()) {
                    webhookUrl = DEFAULT_WEBHOOK;
                    XposedBridge.log("WAHook: webhook(meta missing) -> fallback set");
                } else {
                    XposedBridge.log("WAHook: webhook=" + webhookUrl);
                }
            } else {
                // Context not ready yet; keep fallback
                if (webhookUrl == null || webhookUrl.isEmpty()) webhookUrl = DEFAULT_WEBHOOK;
                XposedBridge.log("WAHook: ctx null on init; using fallback webhook");
            }
        } catch (Throwable t) {
            XposedBridge.log("WAHook: webhook resolve failed: " + t.toString());
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                webhookUrl = DEFAULT_WEBHOOK;
                XposedBridge.log("WAHook: webhook(fallback after error) set");
            }
        }

        // Try hook abstract name
        Class<?> queueClass = XposedHelpers.findClassIfExists("X.AbstractC31841Yd", lpparam.classLoader);
        // Try obfuscated simple name fallback
        if (queueClass == null) queueClass = XposedHelpers.findClassIfExists("X.1Yd", lpparam.classLoader);
        if (queueClass != null) {
        de.robv.android.xposed.XC_MethodHook listHook = new de.robv.android.xposed.XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final List<?> list = (List<?>) param.args[0];
                    if (list == null || list.isEmpty()) return;
                    XposedBridge.log("WAHook: A03 invoked, size=" + list.size());

                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Context ctx = getApplicationContext();
                                if (ctx == null) {
                                    XposedBridge.log("WAHook: ctx null");
                                    return;
                                }
                                for (Object stanza : list) {
                                    try {
                                        String stanzaId = null;
                                        String stanzaKey = null;
                                        String chatJidRaw = null;
                                        String senderJidRaw = null;
                                        long timeSec = 0L;
                                        long createTimeMs = 0L;

                                        try { stanzaId = (String) XposedHelpers.getObjectField(stanza, "A0B"); } catch (Throwable ignored) {}
                                        try {
                                            Object keyObj = XposedHelpers.getObjectField(stanza, "A06");
                                            if (keyObj != null) stanzaKey = (String) XposedHelpers.getObjectField(keyObj, "A00");
                                        } catch (Throwable ignored) {}
                                        try {
                                            Object chatJid = XposedHelpers.getObjectField(stanza, "A08");
                                            if (chatJid != null) {
                                                Method m = chatJid.getClass().getMethod("getRawString");
                                                chatJidRaw = (String) m.invoke(chatJid);
                                            }
                                        } catch (Throwable ignored) {}
                                        try {
                                            Object senderJid = XposedHelpers.getObjectField(stanza, "A09");
                                            if (senderJid != null) {
                                                Method m = senderJid.getClass().getMethod("getRawString");
                                                senderJidRaw = (String) m.invoke(senderJid);
                                            }
                                        } catch (Throwable ignored) {}
                                        try { timeSec = XposedHelpers.getLongField(stanza, "A04"); } catch (Throwable ignored) {}
                                        try { createTimeMs = XposedHelpers.getLongField(stanza, "A02"); } catch (Throwable ignored) {}

                                Intent i = new Intent(ACTION);
                                        i.putExtra("stanza_id", stanzaId);
                                        i.putExtra("stanza_key", stanzaKey);
                                        i.putExtra("chat_jid", chatJidRaw);
                                        i.putExtra("sender_jid", senderJidRaw);
                                        i.putExtra("time_sec", timeSec);
                                        i.putExtra("create_time_ms", createTimeMs);
                                        String payload = "{" +
                                                "\"type\":\"a03\"," +
                                                "\"stanza_id\":\"" + String.valueOf(stanzaId) + "\"," +
                                                "\"stanza_key\":\"" + String.valueOf(stanzaKey) + "\"," +
                                                "\"chat_jid\":\"" + String.valueOf(chatJidRaw) + "\"," +
                                                "\"sender_jid\":\"" + String.valueOf(senderJidRaw) + "\"," +
                                                "\"time_sec\":" + String.valueOf(timeSec) + "," +
                                                "\"create_time_ms\":" + String.valueOf(createTimeMs) +
                                                "}";
                                        i.putExtra("payload", payload);
                                        i.putExtra("s_payload", payload);
                                        // String duplicates for Tasker (%extra_s_*)
                                        if (stanzaId != null) i.putExtra("s_stanza_id", String.valueOf(stanzaId));
                                        if (stanzaKey != null) i.putExtra("s_stanza_key", String.valueOf(stanzaKey));
                                        if (chatJidRaw != null) i.putExtra("s_chat_jid", String.valueOf(chatJidRaw));
                                        if (senderJidRaw != null) i.putExtra("s_sender_jid", String.valueOf(senderJidRaw));
                                        i.putExtra("s_time_sec", String.valueOf(timeSec));
                                        i.putExtra("s_create_time_ms", String.valueOf(createTimeMs));
                                i.setPackage("net.dinglisch.android.taskerm");
                                i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                                ctx.sendBroadcast(i);
                                        XposedBridge.log("WAHook: A03 broadcast sent stanza_id=" + stanzaId);
                                    } catch (Throwable eInner) {
                                        XposedBridge.log("WAHook: per-stanza send error " + eInner.toString());
                                    }
                                }
                            } catch (Throwable e) {
                                XposedBridge.log("WAHook: send error " + e.toString());
                            }
                        }
                    });
                } catch (Throwable ex) {
                    XposedBridge.log("WAHook: hook error " + ex.toString());
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(queueClass, "A03", listHook);
            XposedBridge.log("WAHook: hooked A03 on " + queueClass.getName());
        } catch (Throwable t) {
            XposedBridge.log("WAHook: hook A03 failed: " + t.toString());
        }

        // Also attempt to hook A07(C33T, boolean, boolean) as per jadx dump
        de.robv.android.xposed.XC_MethodHook singleStanzaHook = new de.robv.android.xposed.XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (param.args == null || param.args.length == 0) return;
                    final Object stanza = param.args[0];
                    if (stanza == null) return;
                    XposedBridge.log("WAHook: A07 invoked");

                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Context ctx = getApplicationContext();
                                if (ctx == null) return;

                                String stanzaId = null;
                                String stanzaKey = null;
                                String chatJidRaw = null;
                                String senderJidRaw = null;
                                long timeSec = 0L;
                                long createTimeMs = 0L;

                                try { stanzaId = (String) XposedHelpers.getObjectField(stanza, "A0B"); } catch (Throwable ignored) {}
                                try {
                                    Object keyObj = XposedHelpers.getObjectField(stanza, "A06");
                                    if (keyObj != null) stanzaKey = (String) XposedHelpers.getObjectField(keyObj, "A00");
                                } catch (Throwable ignored) {}
                                try {
                                    Object chatJid = XposedHelpers.getObjectField(stanza, "A08");
                                    if (chatJid != null) {
                                        Method m = chatJid.getClass().getMethod("getRawString");
                                        chatJidRaw = (String) m.invoke(chatJid);
                                    }
                                } catch (Throwable ignored) {}
                                try {
                                    Object senderJid = XposedHelpers.getObjectField(stanza, "A09");
                                    if (senderJid != null) {
                                        Method m = senderJid.getClass().getMethod("getRawString");
                                        senderJidRaw = (String) m.invoke(senderJid);
                                    }
                                } catch (Throwable ignored) {}
                                try { timeSec = XposedHelpers.getLongField(stanza, "A04"); } catch (Throwable ignored) {}
                                try { createTimeMs = XposedHelpers.getLongField(stanza, "A02"); } catch (Throwable ignored) {}

                                Intent i = new Intent(ACTION);
                                i.putExtra("stanza_id", stanzaId);
                                i.putExtra("stanza_key", stanzaKey);
                                i.putExtra("chat_jid", chatJidRaw);
                                i.putExtra("sender_jid", senderJidRaw);
                                i.putExtra("time_sec", timeSec);
                                i.putExtra("create_time_ms", createTimeMs);
                                String payload = "{" +
                                        "\"type\":\"a07\"," +
                                        "\"stanza_id\":\"" + String.valueOf(stanzaId) + "\"," +
                                        "\"stanza_key\":\"" + String.valueOf(stanzaKey) + "\"," +
                                        "\"chat_jid\":\"" + String.valueOf(chatJidRaw) + "\"," +
                                        "\"sender_jid\":\"" + String.valueOf(senderJidRaw) + "\"," +
                                        "\"time_sec\":" + String.valueOf(timeSec) + "," +
                                        "\"create_time_ms\":" + String.valueOf(createTimeMs) +
                                        "}";
                                i.putExtra("payload", payload);
                                i.putExtra("s_payload", payload);
                                // String duplicates for Tasker (%extra_s_*)
                                if (stanzaId != null) i.putExtra("s_stanza_id", String.valueOf(stanzaId));
                                if (stanzaKey != null) i.putExtra("s_stanza_key", String.valueOf(stanzaKey));
                                if (chatJidRaw != null) i.putExtra("s_chat_jid", String.valueOf(chatJidRaw));
                                if (senderJidRaw != null) i.putExtra("s_sender_jid", String.valueOf(senderJidRaw));
                                i.putExtra("s_time_sec", String.valueOf(timeSec));
                                i.putExtra("s_create_time_ms", String.valueOf(createTimeMs));
                                i.setPackage("net.dinglisch.android.taskerm");
                                i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                                ctx.sendBroadcast(i);
                                XposedBridge.log("WAHook: A07 broadcast sent stanza_id=" + stanzaId);
                            } catch (Throwable e) {
                                XposedBridge.log("WAHook: A07 send error " + e.toString());
                            }
                        }
                    });
                } catch (Throwable ex) {
                    XposedBridge.log("WAHook: A07 hook error " + ex.toString());
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(queueClass, "A07", singleStanzaHook);
            XposedBridge.log("WAHook: hooked A07 on " + queueClass.getName());
        } catch (Throwable t) {
            XposedBridge.log("WAHook: hook A07 failed: " + t.toString());
        }
        } else {
            XposedBridge.log("WAHook: queue class not found (both names)");
        }

        // Dynamic hook: if subclasses load later, hook them on the fly
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, boolean.class, new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object result = param.getResult();
                        if (!(result instanceof Class)) return;
                        Class<?> cls = (Class<?>) result;
                        String name = cls.getName();
                        if (!name.startsWith("X.")) return;
                        Class<?> base1 = XposedHelpers.findClassIfExists("X.AbstractC31841Yd", lpparam.classLoader);
                        Class<?> base2 = XposedHelpers.findClassIfExists("X.1Yd", lpparam.classLoader);
                        boolean isSubclass = (base1 != null && base1.isAssignableFrom(cls)) || (base2 != null && base2.isAssignableFrom(cls));
                        if (!isSubclass) return;
                        XposedBridge.log("WAHook: dynamic subclass loaded: " + name);
                        try {
                            de.robv.android.xposed.XC_MethodHook dynListHook = new de.robv.android.xposed.XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    try {
                                        if (param.args == null || param.args.length == 0) return;
                                        List<?> list = (List<?>) param.args[0];
                                        if (list == null || list.isEmpty()) return;
                                        XposedBridge.log("WAHook: A03(dyn) invoked, size=" + list.size());
                                        Context ctx = getApplicationContext();
                                        if (ctx == null) return;
                                        for (Object stanza : list) {
                                            try {
                                                String stanzaId = null, stanzaKey = null, chatJidRaw = null, senderJidRaw = null;
                                                long timeSec = 0L, createTimeMs = 0L;
                                                try { stanzaId = (String) XposedHelpers.getObjectField(stanza, "A0B"); } catch (Throwable ignored) {}
                                                try { Object keyObj = XposedHelpers.getObjectField(stanza, "A06"); if (keyObj != null) stanzaKey = (String) XposedHelpers.getObjectField(keyObj, "A00"); } catch (Throwable ignored) {}
                                                try { Object chatJid = XposedHelpers.getObjectField(stanza, "A08"); if (chatJid != null) { Method m = chatJid.getClass().getMethod("getRawString"); chatJidRaw = (String) m.invoke(chatJid);} } catch (Throwable ignored) {}
                                                try { Object senderJid = XposedHelpers.getObjectField(stanza, "A09"); if (senderJid != null) { Method m = senderJid.getClass().getMethod("getRawString"); senderJidRaw = (String) m.invoke(senderJid);} } catch (Throwable ignored) {}
                                                try { timeSec = XposedHelpers.getLongField(stanza, "A04"); } catch (Throwable ignored) {}
                                                try { createTimeMs = XposedHelpers.getLongField(stanza, "A02"); } catch (Throwable ignored) {}
                                                Intent i = new Intent(ACTION);
                                                i.putExtra("stanza_id", stanzaId);
                                                i.putExtra("stanza_key", stanzaKey);
                                                i.putExtra("chat_jid", chatJidRaw);
                                                i.putExtra("sender_jid", senderJidRaw);
                                                i.putExtra("time_sec", timeSec);
                                                i.putExtra("create_time_ms", createTimeMs);
                                                i.setPackage("net.dinglisch.android.taskerm");
                                                i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                                                ctx.sendBroadcast(i);
                                            } catch (Throwable ignored) {}
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            };
                            XposedBridge.hookAllMethods(cls, "A03", dynListHook);
                        } catch (Throwable ignored) {}
                        try {
                            de.robv.android.xposed.XC_MethodHook dynSingleHook = new de.robv.android.xposed.XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    try {
                                        if (param.args == null || param.args.length == 0) return;
                                        Object stanza = param.args[0];
                                        if (stanza == null) return;
                                        Context ctx = getApplicationContext();
                                        if (ctx == null) return;
                                        String stanzaId = null, stanzaKey = null, chatJidRaw = null, senderJidRaw = null;
                                        long timeSec = 0L, createTimeMs = 0L;
                                        try { stanzaId = (String) XposedHelpers.getObjectField(stanza, "A0B"); } catch (Throwable ignored) {}
                                        try { Object keyObj = XposedHelpers.getObjectField(stanza, "A06"); if (keyObj != null) stanzaKey = (String) XposedHelpers.getObjectField(keyObj, "A00"); } catch (Throwable ignored) {}
                                        try { Object chatJid = XposedHelpers.getObjectField(stanza, "A08"); if (chatJid != null) { Method m = chatJid.getClass().getMethod("getRawString"); chatJidRaw = (String) m.invoke(chatJid);} } catch (Throwable ignored) {}
                                        try { Object senderJid = XposedHelpers.getObjectField(stanza, "A09"); if (senderJid != null) { Method m = senderJid.getClass().getMethod("getRawString"); senderJidRaw = (String) m.invoke(senderJid);} } catch (Throwable ignored) {}
                                        try { timeSec = XposedHelpers.getLongField(stanza, "A04"); } catch (Throwable ignored) {}
                                        try { createTimeMs = XposedHelpers.getLongField(stanza, "A02"); } catch (Throwable ignored) {}
                                        Intent i = new Intent(ACTION);
                                        i.putExtra("stanza_id", stanzaId);
                                        i.putExtra("stanza_key", stanzaKey);
                                        i.putExtra("chat_jid", chatJidRaw);
                                        i.putExtra("sender_jid", senderJidRaw);
                                        i.putExtra("time_sec", timeSec);
                                        i.putExtra("create_time_ms", createTimeMs);
                                        i.setPackage("net.dinglisch.android.taskerm");
                                        i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                                        ctx.sendBroadcast(i);
                                    } catch (Throwable ignored) {}
                                }
                            };
                            XposedBridge.hookAllMethods(cls, "A07", dynSingleHook);
                        } catch (Throwable ignored) {}
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: dynamic hook error " + t.toString());
                    }
                }
            });
            XposedBridge.log("WAHook: dynamic ClassLoader hook installed");
        } catch (Throwable t) {
            XposedBridge.log("WAHook: dynamic ClassLoader hook failed: " + t.toString());
        }

        // Fallback: hook SQLiteDatabase inserts (unordered_stanza_queue and messages)
        try {
            de.robv.android.xposed.XC_MethodHook insertHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        String table = (String) param.args[0];
                        final ContentValues cv = (ContentValues) param.args[2];
                        if (table == null || cv == null) return;

                        boolean isQueue = "unordered_stanza_queue".equals(table);
                        boolean isMessages = "messages".equalsIgnoreCase(table) || table.toLowerCase().contains("message");
                        boolean isPrimaryMessage = "message".equalsIgnoreCase(table);
                        if (!isQueue && !isMessages) return;

                        // Queue fields (unordered_stanza_queue)
                        final String stanzaId = cv.getAsString("stanza_id");
                        final String stanzaKey = cv.getAsString("stanza_key");
                        final String chatJidRawQueue = cv.getAsString("chat_jid");
                        final String senderJidRawQueue = cv.getAsString("sender_jid");
                        final Long timeSecBoxed = cv.getAsLong("time_sec");
                        final Long createTimeMsBoxed = cv.getAsLong("create_time_ms");

                        // Message table fields
                        final String msgKeyId = cv.getAsString("key_id");
                        final String msgChatJid = cv.getAsString("key_remote_jid");
                        final String msgRemoteResource = cv.getAsString("remote_resource");
                        final Integer msgFromMe = cv.getAsInteger("from_me");
                        final Long msgTimestamp = cv.getAsLong("timestamp");
                        final String msgText = cv.getAsString("data");

                        executor.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Context ctx = getApplicationContext();
                                    if (ctx == null) {
                                        XposedBridge.log("WAHook: ctx null (fallback)");
                                        return;
                                    }
                                    Intent i = new Intent(ACTION);
                                    i.putExtra("table", table);
                                    if (isQueue) {
                                        if (stanzaId != null) i.putExtra("stanza_id", stanzaId);
                                        if (stanzaKey != null) i.putExtra("stanza_key", stanzaKey);
                                        if (chatJidRawQueue != null) i.putExtra("chat_jid", chatJidRawQueue);
                                        if (senderJidRawQueue != null) i.putExtra("sender_jid", senderJidRawQueue);
                                        if (timeSecBoxed != null) i.putExtra("time_sec", timeSecBoxed.longValue());
                                        if (createTimeMsBoxed != null) i.putExtra("create_time_ms", createTimeMsBoxed.longValue());
                                        // String duplicates
                                        if (stanzaId != null) i.putExtra("s_stanza_id", String.valueOf(stanzaId));
                                        if (stanzaKey != null) i.putExtra("s_stanza_key", String.valueOf(stanzaKey));
                                        if (chatJidRawQueue != null) i.putExtra("s_chat_jid", String.valueOf(chatJidRawQueue));
                                        if (senderJidRawQueue != null) i.putExtra("s_sender_jid", String.valueOf(senderJidRawQueue));
                                        if (timeSecBoxed != null) i.putExtra("s_time_sec", String.valueOf(timeSecBoxed));
                                        if (createTimeMsBoxed != null) i.putExtra("s_create_time_ms", String.valueOf(createTimeMsBoxed));
                                    }
                                    if (isMessages) {
                                        if (msgKeyId != null) i.putExtra("msg_id", msgKeyId);
                                        if (msgChatJid != null) i.putExtra("chat_jid", msgChatJid);
                                        if (msgRemoteResource != null) i.putExtra("sender_jid", msgRemoteResource);
                                        if (msgFromMe != null) i.putExtra("from_me", msgFromMe.intValue());
                                        if (msgTimestamp != null) i.putExtra("ts", msgTimestamp.longValue());
                                        if (msgText != null) i.putExtra("text", msgText);
                                        // String duplicates for messages
                                        if (msgKeyId != null) i.putExtra("s_msg_id", String.valueOf(msgKeyId));
                                        if (msgChatJid != null) i.putExtra("s_chat_jid", String.valueOf(msgChatJid));
                                        if (msgRemoteResource != null) i.putExtra("s_sender_jid", String.valueOf(msgRemoteResource));
                                        if (msgFromMe != null) i.putExtra("s_from_me", String.valueOf(msgFromMe));
                                        if (msgTimestamp != null) i.putExtra("s_ts", String.valueOf(msgTimestamp));
                                        if (msgText != null) i.putExtra("s_text", String.valueOf(msgText));
                                    }
                                    // JSON payload for webhook/Tasker
                                    String cvJson = buildContentValuesJson(cv);
                                    // Only send for incoming messages if from_me==0 (when present)
                                    if (msgFromMe != null && msgFromMe.intValue() != 0) { return; }
                                    String payload = "{" +
                                            "\"type\":\"sqlite\"," +
                                            "\"table\":\"" + String.valueOf(table) + "\"," +
                                            (stanzaId != null ? "\"stanza_id\":\"" + String.valueOf(stanzaId) + "\"," : "") +
                                            (stanzaKey != null ? "\"stanza_key\":\"" + String.valueOf(stanzaKey) + "\"," : "") +
                                            (chatJidRawQueue != null ? "\"chat_jid\":\"" + String.valueOf(chatJidRawQueue) + "\"," : (msgChatJid != null ? "\"chat_jid\":\"" + String.valueOf(msgChatJid) + "\"," : "")) +
                                            (senderJidRawQueue != null ? "\"sender_jid\":\"" + String.valueOf(senderJidRawQueue) + "\"," : (msgRemoteResource != null ? "\"sender_jid\":\"" + String.valueOf(msgRemoteResource) + "\"," : "")) +
                                            (timeSecBoxed != null ? "\"time_sec\":" + String.valueOf(timeSecBoxed) + "," : "") +
                                            (createTimeMsBoxed != null ? "\"create_time_ms\":" + String.valueOf(createTimeMsBoxed) + "," : (msgTimestamp != null ? "\"ts\":" + String.valueOf(msgTimestamp) + "," : "")) +
                                            (msgKeyId != null ? "\"msg_id\":\"" + String.valueOf(msgKeyId) + "\"," : "") +
                                            (msgText != null ? "\"text\":\"" + jsonEscape(String.valueOf(msgText)) + "\"," : "") +
                                            (msgFromMe != null ? "\"from_me\":" + String.valueOf(msgFromMe) + "," : "") +
                                            (cvJson != null ? "\"cv\":" + cvJson + "," : "") +
                                            "\"source\":\"wahook\"" +
                                            "}";
                                    i.putExtra("payload", payload);
                                    i.putExtra("s_payload", payload);
                                    i.setPackage("net.dinglisch.android.taskerm");
                                    i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                                    ctx.sendBroadcast(i);
                                    XposedBridge.log("WAHook: SQLite insert broadcast table=" + table);
                                    // Suppress sqlite webhook posts; text/media will be sent by media pipeline

                                    // Suppress FTS sqlite_update posts (no external send here)
                                } catch (Throwable e) {
                                    XposedBridge.log("WAHook: fallback send error " + e.toString());
                                }
                            }
                        });

                        // Schedule read-after-insert and media decrypt for insertOrThrow/insertWithOnConflict paths
                        if ("message".equalsIgnoreCase(table)) {
                            final Object dbObj = param.thisObject;
                            final Long sortId = cv.getAsLong("sort_id");
                            Number rowIdNum = null;
                            try { Object r = param.getResult(); if (r instanceof Number) rowIdNum = (Number) r; } catch (Throwable ignored) {}
                            if (sortId != null && sortId > 0) {
                                XposedBridge.log("WAHook: post-insert(IO/IOC) schedule polls sortId=" + sortId);
                                scheduleFtsPoll(dbObj, sortId);
                                scheduleMediaDecrypt(dbObj, sortId);
                            } else if (rowIdNum != null && rowIdNum.longValue() > 0) {
                                long rowId = rowIdNum.longValue();
                                XposedBridge.log("WAHook: post-insert(IO/IOC) schedule polls by rowId=" + rowId);
                                scheduleFtsPoll(dbObj, rowId);
                                scheduleMediaDecrypt(dbObj, rowId);
                            } else {
                                XposedBridge.log("WAHook: post-insert(IO/IOC) missing sort_id/rowId for message");
                            }
                        } else if ("message_media".equalsIgnoreCase(table)) {
                            final Object dbObj = param.thisObject;
                            Long fk = cv.getAsLong("message_row_id");
                            if (fk == null) fk = cv.getAsLong("message");
                            if (fk != null && fk > 0) {
                                XposedBridge.log("WAHook: post-insert(IO/IOC) schedule media by fk=" + fk);
                                scheduleMediaDecrypt(dbObj, fk);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: fallback hook error " + t.toString());
                    }
                }
            };

            XposedHelpers.findAndHookMethod(SQLiteDatabase.class, "insert", String.class, String.class, ContentValues.class, new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        String table = (String) param.args[0];
                        final ContentValues cv = (ContentValues) param.args[2];
                        if (table == null || cv == null) return;
                        // Original behavior from insertHook
                        try {
                            String t = table;
                            ContentValues values = cv;
                            boolean isQueue = "unordered_stanza_queue".equals(t);
                            boolean isMessages = "messages".equalsIgnoreCase(t) || t.toLowerCase().contains("message");
                            boolean isPrimaryMessage = "message".equalsIgnoreCase(t);
                            if (isQueue || isMessages) {
                                // Reuse existing executor block via helper
                                final String tableFinal = t;
                                final ContentValues cvFinal = new ContentValues(values);
                                executor.submit(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            Context ctx = getApplicationContext();
                                            if (ctx == null) return;
                                            Intent i = new Intent(ACTION);
                                            i.putExtra("table", tableFinal);
                                            // minimal broadcast; heavy JSON done below in existing path
                                            ctx.sendBroadcast(i);
                                        } catch (Throwable ignored) {}
                                    }
                                });
                            }
                        } catch (Throwable ignored) {}
                        // Read-after-insert for primary message rows
                        if ("message".equalsIgnoreCase(table)) {
                            final Object dbObj = param.thisObject;
                            final Long sortId = cv.getAsLong("sort_id");
                            Number rowIdNum = null;
                            try { Object r = param.getResult(); if (r instanceof Number) rowIdNum = (Number) r; } catch (Throwable ignored) {}
                            if (sortId != null && sortId > 0) {
                                XposedBridge.log("WAHook: post-insert schedule polls sortId=" + sortId);
                                scheduleFtsPoll(dbObj, sortId);
                                scheduleMediaDecrypt(dbObj, sortId);
                            } else if (rowIdNum != null && rowIdNum.longValue() > 0) {
                                long rowId = rowIdNum.longValue();
                                XposedBridge.log("WAHook: post-insert schedule polls by rowId=" + rowId);
                                scheduleFtsPoll(dbObj, rowId);
                                scheduleMediaDecrypt(dbObj, rowId);
                            } else {
                                XposedBridge.log("WAHook: post-insert missing sort_id/rowId for message");
                            }
                        } else if ("message_media".equalsIgnoreCase(table)) {
                            // If media row arrives first, try schedule by its FK
                            final Object dbObj = param.thisObject;
                            Long fk = cv.getAsLong("message_row_id");
                            if (fk == null) fk = cv.getAsLong("message");
                            if (fk != null && fk > 0) {
                                XposedBridge.log("WAHook: post-insert schedule media by fk=" + fk);
                                scheduleMediaDecrypt(dbObj, fk);
                                mergeMediaFromCv(fk, "message_media", cv);
                                tryDecryptFromCache(dbObj, fk);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedHelpers.findAndHookMethod(SQLiteDatabase.class, "insertOrThrow", String.class, String.class, ContentValues.class, insertHook);
            XposedHelpers.findAndHookMethod(SQLiteDatabase.class, "insertWithOnConflict", String.class, String.class, ContentValues.class, int.class, insertHook);
            // Also hook updates to catch when text/timestamp arrive later
            de.robv.android.xposed.XC_MethodHook updateHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        String table = (String) param.args[0];
                        if (table == null || !"message".equalsIgnoreCase(table)) return;
                        final ContentValues cv = (ContentValues) param.args[1];
                        if (cv == null || cv.size() == 0) return;

                        final String msgKeyId = cv.getAsString("key_id");
                        final String msgChatJid = cv.getAsString("key_remote_jid");
                        final String msgRemoteResource = cv.getAsString("remote_resource");
                        final Integer msgFromMe = cv.getAsInteger("from_me");
                        final Long msgTimestamp = cv.getAsLong("timestamp");
                        final String msgText = cv.getAsString("data");

                        // Only post if что-то значимое обновилось (текст/ts/id) and it's incoming (from_me==0)
                        if (msgText == null && msgTimestamp == null && msgKeyId == null) return;
                        if (msgFromMe != null && msgFromMe.intValue() != 0) return;

                        final String payload = "{" +
                                "\"type\":\"sqlite_update\"," +
                                "\"table\":\"message\"," +
                                (msgKeyId != null ? "\"msg_id\":\"" + String.valueOf(msgKeyId) + "\"," : "") +
                                (msgChatJid != null ? "\"chat_jid\":\"" + String.valueOf(msgChatJid) + "\"," : "") +
                                (msgRemoteResource != null ? "\"sender_jid\":\"" + String.valueOf(msgRemoteResource) + "\"," : "") +
                                (msgFromMe != null ? "\"from_me\":" + String.valueOf(msgFromMe) + "," : "") +
                                (msgTimestamp != null ? "\"ts\":" + String.valueOf(msgTimestamp) + "," : "") +
                                (msgText != null ? "\"text\":\"" + jsonEscape(String.valueOf(msgText)) + "\"," : "") +
                                "\"cv\":" + buildContentValuesJson(cv) + "," +
                                "\"source\":\"wahook\"" +
                                "}";

                        executor.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Context ctx = getApplicationContext();
                                    if (ctx != null) {
                                        Intent i = new Intent(ACTION);
                                        i.putExtra("payload", payload);
                                        i.putExtra("s_payload", payload);
                                        i.setPackage("net.dinglisch.android.taskerm");
                                        i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                                        ctx.sendBroadcast(i);
                                    }
                                } catch (Throwable ignored) {}
                                postJsonAsync(payload);
                            }
                        });
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: update hook error " + t.toString());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(SQLiteDatabase.class, "update", String.class, ContentValues.class, String.class, String[].class, updateHook);
            XposedHelpers.findAndHookMethod(SQLiteDatabase.class, "updateWithOnConflict", String.class, ContentValues.class, String.class, String[].class, int.class, updateHook);
            XposedBridge.log("WAHook: fallback SQLite insert hooks installed");
        } catch (Throwable th) {
            XposedBridge.log("WAHook: fallback SQLite hook setup failed: " + th.toString());
        }

        // Fallback: hook NotificationManager.notify to capture incoming messages from notifications
        try {
            Class<?> nmClass = XposedHelpers.findClass("android.app.NotificationManager", null);
            de.robv.android.xposed.XC_MethodHook notifyHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Context ctx = getApplicationContext();
                        if (ctx == null) return;
                        if (!param.method.getName().equals("notify") || param.args == null) return;

                        String tag = null;
                        int id;
                        android.app.Notification notification;

                        if (param.args.length == 2) { // (int id, Notification n)
                            id = (Integer) param.args[0];
                            notification = (android.app.Notification) param.args[1];
                        } else if (param.args.length == 3) { // (String tag, int id, Notification n)
                            tag = (String) param.args[0];
                            id = (Integer) param.args[1];
                            notification = (android.app.Notification) param.args[2];
                        } else {
                            return;
                        }

                        if (notification == null) return;
                        android.os.Bundle extras = notification.extras;
                        String title = extras != null ? extras.getString(android.app.Notification.EXTRA_TITLE) : null;
                        CharSequence textCs = extras != null ? extras.getCharSequence(android.app.Notification.EXTRA_TEXT) : null;
                        String text = textCs != null ? textCs.toString() : null;

                        Intent i = new Intent(ACTION);
                        i.putExtra("notif_title", title);
                        i.putExtra("notif_text", text);
                        i.putExtra("notif_tag", tag);
                        i.putExtra("notif_id", id);
                        // String duplicates
                        if (title != null) i.putExtra("s_notif_title", String.valueOf(title));
                        if (text != null) i.putExtra("s_notif_text", String.valueOf(text));
                        if (tag != null) i.putExtra("s_notif_tag", String.valueOf(tag));
                        i.putExtra("s_notif_id", String.valueOf(id));
                        String payload = "{" +
                                "\"type\":\"notify\"," +
                                (title != null ? "\"notif_title\":\"" + jsonEscape(String.valueOf(title)) + "\"," : "") +
                                (text != null ? "\"notif_text\":\"" + jsonEscape(String.valueOf(text)) + "\"," : "") +
                                (tag != null ? "\"notif_tag\":\"" + String.valueOf(tag) + "\"," : "") +
                                "\"notif_id\":" + String.valueOf(id) +
                                "}";
                        i.putExtra("payload", payload);
                        i.putExtra("s_payload", payload);
                        i.setPackage("net.dinglisch.android.taskerm");
                        i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                        ctx.sendBroadcast(i);
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: notify hook error " + t.toString());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(nmClass, "notify", int.class, android.app.Notification.class, notifyHook);
            XposedHelpers.findAndHookMethod(nmClass, "notify", String.class, int.class, android.app.Notification.class, notifyHook);
            XposedBridge.log("WAHook: notification hooks installed");
        } catch (Throwable th) {
            XposedBridge.log("WAHook: notification hook setup failed: " + th.toString());
        }

        // Hook ContentResolver.insert/bulkInsert for WhatsApp providers (message)
        try {
            de.robv.android.xposed.XC_MethodHook crHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args == null || param.args.length < 2) return;
                        Uri uri = (Uri) param.args[0];
                        if (uri == null) return;
                        String auth = uri.getAuthority();
                        String path = uri.getPath();
                        if (auth == null || path == null) return;
                        if (!auth.startsWith("com.whatsapp.provider")) return;
                        if (!(path.contains("message") || path.contains("fts"))) return;

                        if (param.method.getName().equals("insert")) {
                            ContentValues cv = (ContentValues) param.args[1];
                            if (cv != null) handleCvAndPost("contentresolver_insert", cv, uri);
                            if (path.contains("message_media")) {
                                Long fk = cv.getAsLong("message_row_id");
                                if (fk == null) fk = cv.getAsLong("message");
                                if (fk != null && fk > 0) {
                                    mergeMediaFromCv(fk, "cr_insert", cv);
                                    tryDecryptFromCache(getApplicationContext(), fk);
                                }
                            }
                        } else if (param.method.getName().equals("bulkInsert")) {
                            ContentValues[] arr = (ContentValues[]) param.args[1];
                            if (arr != null) {
                                for (ContentValues cv : arr) {
                                    if (cv != null) handleCvAndPost("contentresolver_bulk", cv, uri);
                                    if (path.contains("message_media")) {
                                        Long fk = cv.getAsLong("message_row_id");
                                        if (fk == null) fk = cv.getAsLong("message");
                                        if (fk != null && fk > 0) {
                                            mergeMediaFromCv(fk, "cr_bulk", cv);
                                            tryDecryptFromCache(getApplicationContext(), fk);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: CR hook err " + t.toString());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(ContentResolver.class, "insert", Uri.class, ContentValues.class, crHook);
            XposedHelpers.findAndHookMethod(ContentResolver.class, "bulkInsert", Uri.class, ContentValues[].class, crHook);
            // update for message_ftsv2 long texts
            de.robv.android.xposed.XC_MethodHook crUpdateHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args == null || param.args.length < 4) return;
                        Uri uri = (Uri) param.args[0];
                        if (uri == null) return;
                        String auth = uri.getAuthority();
                        String path = uri.getPath();
                        if (auth == null || path == null) return;
                        if (!auth.startsWith("com.whatsapp.provider")) return;
                        if (!path.contains("message_ftsv2")) return;
                        ContentValues cv = (ContentValues) param.args[1];
                        if (cv == null) return;
                        // suppress FTS contentresolver update posts
                        // If we see media columns here (some builds write via provider), merge and attempt decrypt
                        if (path.contains("message_media")) {
                            Long fk = cv.getAsLong("message_row_id");
                            if (fk == null) fk = cv.getAsLong("message");
                            if (fk != null && fk > 0) {
                                mergeMediaFromCv(fk, "cr_update", cv);
                                tryDecryptFromCache(getApplicationContext() != null ? getApplicationContext() : null, fk);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: CR update hook err " + t.toString());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(ContentResolver.class, "update", Uri.class, ContentValues.class, String.class, String[].class, crUpdateHook);
            // applyBatch for batched updates/inserts
            de.robv.android.xposed.XC_MethodHook crApplyHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args == null || param.args.length < 2) return;
                        String authority = (String) param.args[0];
                        if (authority == null || !authority.startsWith("com.whatsapp.provider")) return;
                        Object opsObj = param.args[1];
                        if (!(opsObj instanceof java.util.ArrayList)) return;
                        java.util.ArrayList<?> ops = (java.util.ArrayList<?>) opsObj;
                        for (Object op : ops) {
                            try {
                                Uri uri = null;
                                ContentValues values = null;
                                try {
                                    java.lang.reflect.Method mu = op.getClass().getMethod("getUri");
                                    uri = (Uri) mu.invoke(op);
                                } catch (Throwable ignored) {
                                    try { uri = (Uri) XposedHelpers.getObjectField(op, "mUri"); } catch (Throwable ignored2) {}
                                }
                                if (uri == null) continue;
                                String path = uri.getPath();
                                if (path == null || !path.contains("message_ftsv2")) continue;
                                try {
                                    java.lang.reflect.Method mv = op.getClass().getMethod("getValues");
                                    values = (ContentValues) mv.invoke(op);
                                } catch (Throwable ignored) {
                                    try { values = (ContentValues) XposedHelpers.getObjectField(op, "mValues"); } catch (Throwable ignored2) {}
                                }
                                if (values != null) {
                                    // suppress FTS contentresolver applyBatch posts
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: CR applyBatch hook err " + t.toString());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(ContentResolver.class, "applyBatch", String.class, java.util.ArrayList.class, crApplyHook);
            XposedBridge.log("WAHook: ContentResolver hooks installed");
        } catch (Throwable th) {
            XposedBridge.log("WAHook: ContentResolver hook setup failed: " + th.toString());
        }

        // Hook ContentProvider.insert/bulkInsert
        try {
            de.robv.android.xposed.XC_MethodHook cpHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args == null || param.args.length < 2) return;
                        Uri uri = (Uri) param.args[0];
                        if (uri == null) return;
                        String auth = uri.getAuthority();
                        String path = uri.getPath();
                        if (auth == null || path == null) return;
                        if (!auth.startsWith("com.whatsapp.provider")) return;
                        if (!(path.contains("message") || path.contains("fts"))) return;

                        if (param.method.getName().equals("insert")) {
                            ContentValues cv = (ContentValues) param.args[1];
                            if (cv != null) handleCvAndPost("contentprovider_insert", cv, uri);
                        } else if (param.method.getName().equals("bulkInsert")) {
                            ContentValues[] arr = (ContentValues[]) param.args[1];
                            if (arr != null) {
                                for (ContentValues cv : arr) {
                                    if (cv != null) handleCvAndPost("contentprovider_bulk", cv, uri);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: CP hook err " + t.toString());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(ContentProvider.class, "insert", Uri.class, ContentValues.class, cpHook);
            XposedHelpers.findAndHookMethod(ContentProvider.class, "bulkInsert", Uri.class, ContentValues[].class, cpHook);
            // update for message_ftsv2
            de.robv.android.xposed.XC_MethodHook cpUpdateHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args == null || param.args.length < 4) return;
                        Uri uri = (Uri) param.args[0];
                        if (uri == null) return;
                        String auth = uri.getAuthority();
                        String path = uri.getPath();
                        if (auth == null || path == null) return;
                        if (!auth.startsWith("com.whatsapp.provider")) return;
                        if (!(path.contains("message_ftsv2") || path.contains("fts"))) return;
                        ContentValues cv = (ContentValues) param.args[1];
                        if (cv == null) return;
                        handleFtsCvAndPost("contentprovider_update", cv, uri);
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: CP update hook err " + t.toString());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(ContentProvider.class, "update", Uri.class, ContentValues.class, String.class, String[].class, cpUpdateHook);
            // applyBatch
            de.robv.android.xposed.XC_MethodHook cpApplyHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args == null || param.args.length < 2) return;
                        String authority = (String) param.args[0];
                        if (authority == null || !authority.startsWith("com.whatsapp.provider")) return;
                        Object opsObj = param.args[1];
                        if (!(opsObj instanceof java.util.ArrayList)) return;
                        java.util.ArrayList<?> ops = (java.util.ArrayList<?>) opsObj;
                        for (Object op : ops) {
                            try {
                                Uri uri = null;
                                ContentValues values = null;
                                try { java.lang.reflect.Method mu = op.getClass().getMethod("getUri"); uri = (Uri) mu.invoke(op); } catch (Throwable ignored) { try { uri = (Uri) XposedHelpers.getObjectField(op, "mUri"); } catch (Throwable ignored2) {} }
                                if (uri == null) continue;
                                String path = uri.getPath();
                                if (path == null || !(path.contains("message_ftsv2") || path.contains("fts"))) continue;
                                try { java.lang.reflect.Method mv = op.getClass().getMethod("getValues"); values = (ContentValues) mv.invoke(op); } catch (Throwable ignored) { try { values = (ContentValues) XposedHelpers.getObjectField(op, "mValues"); } catch (Throwable ignored2) {} }
                                if (values != null) { /* suppress contentprovider_applyBatch for FTS */ }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("WAHook: CP applyBatch hook err " + t.toString());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(ContentProvider.class, "applyBatch", String.class, java.util.ArrayList.class, cpApplyHook);
            XposedBridge.log("WAHook: ContentProvider hooks installed");
        } catch (Throwable th) {
            XposedBridge.log("WAHook: ContentProvider hook setup failed: " + th.toString());
        }

        // Low-level SQL fallback: execSQL and SQLiteStatement for message_ftsv2 long texts
        try {
            de.robv.android.xposed.XC_MethodHook execWithArgsHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        String sql = (String) param.args[0];
                        Object[] bindArgs = (Object[]) param.args[1];
                        handleSqlIfFts("execSQL_args", sql, bindArgs);
                    } catch (Throwable ignored) {}
                }
            };
            de.robv.android.xposed.XC_MethodHook execNoArgsHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        String sql = (String) param.args[0];
                        handleSqlIfFts("execSQL", sql, null);
                    } catch (Throwable ignored) {}
                }
            };
            XposedHelpers.findAndHookMethod(SQLiteDatabase.class, "execSQL", String.class, Object[].class, execWithArgsHook);
            XposedHelpers.findAndHookMethod(SQLiteDatabase.class, "execSQL", String.class, execNoArgsHook);

            de.robv.android.xposed.XC_MethodHook stmtHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object stmt = param.thisObject;
                        String sql = null;
                        Object[] args = null;
                        try { sql = (String) XposedHelpers.getObjectField(stmt, "mSql"); } catch (Throwable ignored) {}
                        try { Object a = XposedHelpers.getObjectField(stmt, "mBindArgs"); if (a instanceof Object[]) args = (Object[]) a; } catch (Throwable ignored) {}
                        if (args == null) {
                            args = stmtBindArgs.get(stmt);
                        }
                        handleSqlIfFts("statement", sql, args);
                        if (stmt != null) stmtBindArgs.remove(stmt);
                    } catch (Throwable ignored) {}
                }
            };
            XposedHelpers.findAndHookMethod(SQLiteStatement.class, "executeInsert", stmtHook);
            XposedHelpers.findAndHookMethod(SQLiteStatement.class, "executeUpdateDelete", stmtHook);
            // bind hooks to collect args
            de.robv.android.xposed.XC_MethodHook bindStringHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object stmt = param.thisObject; int idx = (Integer) param.args[0]; String val = (String) param.args[1];
                    storeBindArg(stmt, idx, val);
                }
            };
            de.robv.android.xposed.XC_MethodHook bindBlobHook = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object stmt = param.thisObject; int idx = (Integer) param.args[0]; Object blob = param.args[1];
                    storeBindArg(stmt, idx, blob);
                }
            };
            XposedHelpers.findAndHookMethod(SQLiteStatement.class, "bindString", int.class, String.class, bindStringHook);
            XposedHelpers.findAndHookMethod(SQLiteStatement.class, "bindBlob", int.class, byte[].class, bindBlobHook);
            XposedBridge.log("WAHook: SQL fallback hooks installed");
        } catch (Throwable th) {
            XposedBridge.log("WAHook: SQL fallback hook setup failed: " + th.toString());
        }

        // SQLCipher fallback: same hooks for net.sqlcipher.database.*
        try {
            de.robv.android.xposed.XC_MethodHook execWithArgsHook2 = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try { String sql = (String) param.args[0]; Object[] bindArgs = (Object[]) param.args[1]; handleSqlIfFts("cipher_execSQL_args", sql, bindArgs);} catch (Throwable ignored) {}
                }
            };
            de.robv.android.xposed.XC_MethodHook execNoArgsHook2 = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try { String sql = (String) param.args[0]; handleSqlIfFts("cipher_execSQL", sql, null);} catch (Throwable ignored) {}
                }
            };
            Class<?> cipherDb = XposedHelpers.findClassIfExists("net.sqlcipher.database.SQLiteDatabase", lpparam.classLoader);
            if (cipherDb != null) {
                XposedHelpers.findAndHookMethod(cipherDb, "execSQL", String.class, Object[].class, execWithArgsHook2);
                XposedHelpers.findAndHookMethod(cipherDb, "execSQL", String.class, execNoArgsHook2);
            }

            de.robv.android.xposed.XC_MethodHook stmtHook2 = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object stmt = param.thisObject;
                        String sql = null; Object[] args = null;
                        try { sql = (String) XposedHelpers.getObjectField(stmt, "mSql"); } catch (Throwable ignored) {}
                        try { Object a = XposedHelpers.getObjectField(stmt, "mBindArgs"); if (a instanceof Object[]) args = (Object[]) a; } catch (Throwable ignored) {}
                        if (args == null) { args = stmtBindArgs.get(stmt); }
                        handleSqlIfFts("cipher_statement", sql, args);
                        if (stmt != null) stmtBindArgs.remove(stmt);
                    } catch (Throwable ignored) {}
                }
            };
            Class<?> cipherStmt = XposedHelpers.findClassIfExists("net.sqlcipher.database.SQLiteStatement", lpparam.classLoader);
            if (cipherStmt != null) {
                XposedHelpers.findAndHookMethod(cipherStmt, "executeInsert", stmtHook2);
                XposedHelpers.findAndHookMethod(cipherStmt, "executeUpdateDelete", stmtHook2);
                // bind hooks
                de.robv.android.xposed.XC_MethodHook cipherBindStringHook = new de.robv.android.xposed.XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object stmt = param.thisObject; int idx = (Integer) param.args[0]; String val = (String) param.args[1];
                        storeBindArg(stmt, idx, val);
                    }
                };
                de.robv.android.xposed.XC_MethodHook cipherBindBlobHook = new de.robv.android.xposed.XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object stmt = param.thisObject; int idx = (Integer) param.args[0]; Object blob = param.args[1];
                        storeBindArg(stmt, idx, blob);
                    }
                };
                XposedHelpers.findAndHookMethod(cipherStmt, "bindString", int.class, String.class, cipherBindStringHook);
                XposedHelpers.findAndHookMethod(cipherStmt, "bindBlob", int.class, byte[].class, cipherBindBlobHook);
            }
            XposedBridge.log("WAHook: SQLCipher fallback hooks installed");
        } catch (Throwable th) {
            XposedBridge.log("WAHook: SQLCipher fallback hook setup failed: " + th.toString());
        }

        // High-level hook: X.C02690Bf.A09(AbstractC28851Mq, Map, long, boolean)
        try {
            Class<?> ftsStore = XposedHelpers.findClassIfExists("X.C02690Bf", lpparam.classLoader);
            if (ftsStore != null) {
                XposedHelpers.findAndHookMethod(ftsStore, "A09", Object.class, java.util.Map.class, long.class, boolean.class,
                        new de.robv.android.xposed.XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    Object msgObj = param.args[0];
                                    if (msgObj == null) return;
                                    String text = null;
                                    try { text = (String) XposedHelpers.callMethod(param.thisObject, "A0F", msgObj); } catch (Throwable ignored) {}
                                    if (text == null) return;
                                    Long docid = null;
                                    try {
                                        Object res = param.getResult();
                                        if (res != null) {
                                            try { docid = XposedHelpers.getLongField(res, "A02"); } catch (Throwable ignored) {}
                                        }
                                        if (docid == null) {
                                            try { docid = XposedHelpers.getLongField(msgObj, "A0k"); } catch (Throwable ignored) {}
                                        }
                                    } catch (Throwable ignored) {}
                                    String payload = "{" +
                                            "\"type\":\"fts_from_a09\"," +
                                            (docid != null ? "\"docid\":" + String.valueOf(docid) + "," : "") +
                                            "\"text\":\"" + jsonEscape(String.valueOf(text)) + "\"," +
                                            "\"source\":\"wahook\"" +
                                            "}";
                                    // do not post fts_from_a09 externally
                                    try { Context ctx = getApplicationContext(); if (ctx != null) { Intent i = new Intent(ACTION); i.putExtra("payload", payload); i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES); ctx.sendBroadcast(i);} } catch (Throwable ignored) {}
                                } catch (Throwable t) {
                                    XposedBridge.log("WAHook: A09 hook err " + t.toString());
                                }
                            }
                        });
                XposedBridge.log("WAHook: hooked C02690Bf.A09");
            }
        } catch (Throwable t) {
            XposedBridge.log("WAHook: hook C02690Bf.A09 failed: " + t.toString());
        }

        // Final store hooks: X.C04800Mj (INSERT_FTS_MESSAGE / UPDATE_FTS_MESSAGE)
        try {
            Class<?> storeClass = XposedHelpers.findClassIfExists("X.C04800Mj", lpparam.classLoader);
            if (storeClass != null) {
                // A06(String table, String opTag, ContentValues cv)
                try {
                    XposedHelpers.findAndHookMethod(storeClass, "A06", String.class, String.class, ContentValues.class,
                            new de.robv.android.xposed.XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    try {
                                        String table = (String) param.args[0];
                                        ContentValues cv = (ContentValues) param.args[2];
                                        if (table == null || cv == null) return;
                                        if (!("message_ftsv2".equalsIgnoreCase(table) || "message_newsletter_fts".equalsIgnoreCase(table))) return;
                                        String text = cv.getAsString("content");
                                        Long docid = cv.getAsLong("docid");
                                        String ftsJid = cv.getAsString("fts_jid");
                                        String ftsNs = cv.getAsString("fts_namespace");
                                        String payload = "{" +
                                                "\"type\":\"fts_insert\"," +
                                                "\"table\":\"" + table + "\"," +
                                                (docid != null ? "\"docid\":" + String.valueOf(docid) + "," : "") +
                                                (ftsJid != null ? "\"fts_jid\":\"" + jsonEscape(ftsJid) + "\"," : "") +
                                                (ftsNs != null ? "\"fts_namespace\":\"" + jsonEscape(ftsNs) + "\"," : "") +
                                                (text != null ? "\"text\":\"" + jsonEscape(text) + "\"," : "") +
                                                "\"cv\":" + buildContentValuesJson(cv) + "," +
                                                "\"source\":\"wahook\"" +
                                                "}";
                                        // do not post direct FTS insert externally
                                        try { Context ctx = getApplicationContext(); if (ctx != null) { Intent i = new Intent(ACTION); i.putExtra("payload", payload); i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES); ctx.sendBroadcast(i); } } catch (Throwable ignored) {}
                                    } catch (Throwable t) {
                                        XposedBridge.log("WAHook: C04800Mj.A06 hook err " + t.toString());
                                    }
                                }
                            });
                } catch (Throwable t) {
                    XposedBridge.log("WAHook: hook A06 failed: " + t.toString());
                }

                // A02(ContentValues cv, String table, String where, String opTag, String[] args)
                try {
                    XposedHelpers.findAndHookMethod(storeClass, "A02", ContentValues.class, String.class, String.class, String.class, String[].class,
                            new de.robv.android.xposed.XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    try {
                                        ContentValues cv = (ContentValues) param.args[0];
                                        String table = (String) param.args[1];
                                        String where = (String) param.args[2];
                                        String[] args = (String[]) param.args[4];
                                        if (table == null) return;
                                        if (!("message_ftsv2".equalsIgnoreCase(table) || "message_newsletter_fts".equalsIgnoreCase(table))) return;
                                        Long docid = null;
                                        if (cv != null) docid = cv.getAsLong("docid");
                                        if (docid == null && where != null && where.replace(" ", "").equalsIgnoreCase("docid=?") && args != null && args.length > 0) {
                                            try { docid = Long.parseLong(args[0]); } catch (Throwable ignored) {}
                                        }
                                        String text = cv != null ? cv.getAsString("content") : null;
                                        String ftsJid = cv != null ? cv.getAsString("fts_jid") : null;
                                        String ftsNs = cv != null ? cv.getAsString("fts_namespace") : null;
                                        String payload = "{" +
                                                "\"type\":\"fts_update\"," +
                                                "\"table\":\"" + table + "\"," +
                                                (docid != null ? "\"docid\":" + String.valueOf(docid) + "," : "") +
                                                (ftsJid != null ? "\"fts_jid\":\"" + jsonEscape(ftsJid) + "\"," : "") +
                                                (ftsNs != null ? "\"fts_namespace\":\"" + jsonEscape(ftsNs) + "\"," : "") +
                                                (text != null ? "\"text\":\"" + jsonEscape(text) + "\"," : "") +
                                                (cv != null ? "\"cv\":" + buildContentValuesJson(cv) + "," : "") +
                                                "\"source\":\"wahook\"" +
                                                "}";
                                        // do not post direct FTS update externally
                                        try { Context ctx = getApplicationContext(); if (ctx != null) { Intent i = new Intent(ACTION); i.putExtra("payload", payload); i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES); ctx.sendBroadcast(i); } } catch (Throwable ignored) {}
                                    } catch (Throwable t) {
                                        XposedBridge.log("WAHook: C04800Mj.A02 hook err " + t.toString());
                                    }
                                }
                            });
                } catch (Throwable t) {
                    XposedBridge.log("WAHook: hook A02 failed: " + t.toString());
                }
            } else {
                XposedBridge.log("WAHook: store class X.C04800Mj not found");
            }
        } catch (Throwable t) {
            XposedBridge.log("WAHook: store hooks setup failed: " + t.toString());
        }
    }
    private void storeBindArg(Object stmt, int idx, Object val) {
        try {
            if (stmt == null) return;
            Object[] args = stmtBindArgs.get(stmt);
            int at = Math.max(idx, 1);
            if (args == null || args.length < at + 1) {
                int newLen = Math.max(16, at + 1);
                Object[] n = new Object[newLen];
                if (args != null) System.arraycopy(args, 0, n, 0, args.length);
                args = n;
            }
            args[idx] = val;
            stmtBindArgs.put(stmt, args);
        } catch (Throwable ignored) {}
    }

    private static Context getApplicationContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method m = activityThread.getMethod("currentApplication");
            Object app = m.invoke(null);
            return (Context) app;
        } catch (Throwable t) {
            XposedBridge.log("WAHook: getApplicationContext failed: " + t.toString());
            return null;
        }
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private void postJsonAsync(final String jsonPayload) {
        if (jsonPayload == null) return;
        final String url = webhookUrl;
        if (url == null || !url.startsWith("http")) return;
        executor.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(url);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] body = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    conn.setFixedLengthStreamingMode(body.length);
                    OutputStream os = conn.getOutputStream();
                    os.write(body);
                    os.flush();
                    os.close();
                    int code = conn.getResponseCode();
                    XposedBridge.log("WAHook: webhook POST -> " + url + " code=" + code);
                    if (code / 100 != 2) {
                        XposedBridge.log("WAHook: webhook HTTP " + code);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("WAHook: webhook error " + t.toString());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private void scheduleFtsPoll(final Object sqliteDbObj, final long docid) {
        final long[] delaysMs = new long[] { 200, 400, 800, 1200, 1800, 2500, 3500, 5000 };
        for (final long d : delaysMs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(d);
                    } catch (InterruptedException ignored) {}
                    try {
                        String content = queryFtsContent(sqliteDbObj, docid);
                        if (content != null && !content.isEmpty()) {
                            // Silence FTS read-after-insert posts; text will be sent by message/text pipeline
                        } else {
                            XposedBridge.log("WAHook: fts poll miss docid=" + docid + " delayMs=" + d);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    // ============ MEDIA PIPELINE ============
    private void scheduleMediaDecrypt(final Object sqliteDbObj, final long msgId) {
        final long[] delaysMs = new long[] { 200, 600, 1200, 2000, 3500, 5000 };
        for (final long d : delaysMs) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try { Thread.sleep(d); } catch (InterruptedException ignored) {}
                    try {
                        // Skip if already sent (avoid noisy logs)
                        try {
                            MediaInfo cached = mediaCache.get(msgId);
                            if (cached != null && cached.sent) { return; }
                        } catch (Throwable ignored) {}
                        XposedBridge.log("WAHook: media poll start msgId=" + msgId + " delayMs=" + d);
                        // Step 1: read message_type, from_me, caption, chat info, and timestamp
                        Integer type = null; String caption = null; Integer fromMe = null; Long chatRowId = null; Long timestamp = null;
                        Object c = rawQueryAnyDb(sqliteDbObj, "SELECT message_type, from_me, text_data, chat_row_id, timestamp FROM message WHERE sort_id=? OR _id=? OR rowid=? LIMIT 1", new String[]{ String.valueOf(msgId), String.valueOf(msgId), String.valueOf(msgId) }, "WAHook/mediaType");
                        if (c != null) {
                            try {
                                Boolean mv = (Boolean) XposedHelpers.callMethod(c, "moveToFirst");
                                if (mv != null && mv) {
                                    int idxT = (Integer) XposedHelpers.callMethod(c, "getColumnIndexOrThrow", "message_type");
                                    int idxFM = (Integer) XposedHelpers.callMethod(c, "getColumnIndexOrThrow", "from_me");
                                    int idxCap = (Integer) XposedHelpers.callMethod(c, "getColumnIndexOrThrow", "text_data");
                                    int idxChat = (Integer) XposedHelpers.callMethod(c, "getColumnIndexOrThrow", "chat_row_id");
                                    int idxTs = (Integer) XposedHelpers.callMethod(c, "getColumnIndexOrThrow", "timestamp");
                                    type = (Integer) XposedHelpers.callMethod(c, "getInt", idxT);
                                    fromMe = (Integer) XposedHelpers.callMethod(c, "getInt", idxFM);
                                    caption = (String) XposedHelpers.callMethod(c, "getString", idxCap);
                                    chatRowId = (Long) XposedHelpers.callMethod(c, "getLong", idxChat);
                                    timestamp = (Long) XposedHelpers.callMethod(c, "getLong", idxTs);
                                    XposedBridge.log("WAHook: media row found type=" + type + ", from_me=" + fromMe + ", chatRowId=" + chatRowId + ", ts=" + timestamp + ", captionLen=" + (caption==null?0:caption.length()));
                                }
                            } finally { try { XposedHelpers.callMethod(c, "close"); } catch (Throwable ignored) {} }
                        }
                        // Step 2: resolve chat JID and name
                        String chatJid = null; String chatName = null;
                        if (chatRowId != null && chatRowId > 0) {
                            try {
                                Object cj = rawQueryAnyDb(sqliteDbObj, "SELECT j.user, j.server FROM chat c JOIN jid j ON c.jid_row_id = j._id WHERE c._id=? LIMIT 1", new String[]{ String.valueOf(chatRowId) }, "WAHook/chatJid");
                                if (cj != null) {
                                    try {
                                        Boolean mvj = (Boolean) XposedHelpers.callMethod(cj, "moveToFirst");
                                        if (mvj != null && mvj) {
                                            String user = (String) XposedHelpers.callMethod(cj, "getString", 0);
                                            String server = (String) XposedHelpers.callMethod(cj, "getString", 1);
                                            chatJid = user + "@" + server;
                                        }
                                    } finally { try { XposedHelpers.callMethod(cj, "close"); } catch (Throwable ignored) {} }
                                }
                            } catch (Throwable ignored) {}
                            // Try to get contact name
                            try {
                                Object cn = rawQueryAnyDb(sqliteDbObj, "SELECT display_name FROM wa_contacts WHERE jid IN (SELECT raw_string FROM jid WHERE _id=(SELECT jid_row_id FROM chat WHERE _id=?)) LIMIT 1", new String[]{ String.valueOf(chatRowId) }, "WAHook/chatName");
                                if (cn != null) {
                                    try {
                                        Boolean mvn = (Boolean) XposedHelpers.callMethod(cn, "moveToFirst");
                                        if (mvn != null && mvn) {
                                            chatName = (String) XposedHelpers.callMethod(cn, "getString", 0);
                                        }
                                    } finally { try { XposedHelpers.callMethod(cn, "close"); } catch (Throwable ignored) {} }
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (fromMe != null && fromMe != 0) { XposedBridge.log("WAHook: skip outgoing msgId=" + msgId); return; }
                        if (type == null) { XposedBridge.log("WAHook: media skip (type null) msgId=" + msgId); return; }
                        if (type == 0) {
                            // Plain text message: send text immediately from text_data
                            // Check if already sent to dedupe
                            try {
                                MediaInfo cached = mediaCache.get(msgId);
                                if (cached != null && cached.sent) {
                                    return;
                                }
                            } catch (Throwable ignored) {}
                            if (caption != null && !caption.isEmpty()) {
                                String payload = "{" +
                                        "\"type\":\"text\"," +
                                        "\"docid\":" + msgId + "," +
                                        "\"text\":\"" + jsonEscape(caption) + "\"," +
                                        (chatJid != null ? "\"chat_jid\":\"" + jsonEscape(chatJid) + "\"," : "") +
                                        (chatName != null ? "\"chat_name\":\"" + jsonEscape(chatName) + "\"," : "") +
                                        (timestamp != null ? "\"timestamp\":" + timestamp + "," : "") +
                                        "\"source\":\"wahook\"" +
                                        "}";
                                XposedBridge.log("WAHook: text webhook post msgId=" + msgId + " textLen=" + caption.length() + " from=" + chatJid + " ts=" + timestamp);
                                postJsonAsync(payload);
                                try { Context ctx = getApplicationContext(); if (ctx != null) { Intent i = new Intent(ACTION); i.putExtra("payload", payload); i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES); ctx.sendBroadcast(i);} } catch (Throwable ignored) {}
                                // Mark as sent
                                try { MediaInfo cached = mediaCache.computeIfAbsent(msgId, k -> new MediaInfo()); cached.sent = true; } catch (Throwable ignored) {}
                            } else {
                                XposedBridge.log("WAHook: text message but caption empty msgId=" + msgId);
                            }
                            return;
                        }

                        MediaInfo mi = queryMediaInfo(sqliteDbObj, msgId);
                        if (mi == null) {
                            XposedBridge.log("WAHook: media info not found msgId=" + msgId + ", will rely on cache if later rows arrive");
                            tryDecryptFromCache(sqliteDbObj, msgId);
                            return;
                        }
                        // Merge into cache and re-read merged view (avoid Java 8 lambda captures)
                        try {
                            MediaInfo old = mediaCache.get(msgId);
                            MediaInfo tgt = (old != null) ? old : new MediaInfo();
                            if (tgt.mediaKeyB64 == null || tgt.mediaKeyB64.isEmpty()) tgt.mediaKeyB64 = mi.mediaKeyB64;
                            if (tgt.directPath == null || tgt.directPath.isEmpty()) tgt.directPath = mi.directPath;
                            if (tgt.messageUrl == null || tgt.messageUrl.isEmpty()) tgt.messageUrl = mi.messageUrl;
                            if (tgt.mimeType == null || tgt.mimeType.isEmpty()) tgt.mimeType = mi.mimeType;
                            if (tgt.fileLength == 0 && mi.fileLength > 0) tgt.fileLength = mi.fileLength;
                            mediaCache.put(msgId, tgt);
                        } catch (Throwable ignored) {}
                        try { MediaInfo merged = mediaCache.get(msgId); if (merged != null) mi = merged; } catch (Throwable ignored) {}
                        boolean hasKey = mi.mediaKeyB64 != null && !mi.mediaKeyB64.isEmpty();
                        boolean hasPath = (mi.directPath != null && !mi.directPath.isEmpty()) || (mi.messageUrl != null && !mi.messageUrl.isEmpty());
                        XposedBridge.log("WAHook: media info msgId=" + msgId + " mime=" + mi.mimeType + " len=" + mi.fileLength + " hasKey=" + hasKey + " hasPath=" + hasPath);
                        if (!hasKey || !hasPath) { XposedBridge.log("WAHook: media missing key or path msgId=" + msgId + ", try cache"); tryDecryptFromCache(sqliteDbObj, msgId); return; }

                        byte[] enc = downloadEnc(mi);
                        if (enc == null || enc.length == 0) { XposedBridge.log("WAHook: media download empty msgId=" + msgId); return; }
                        XposedBridge.log("WAHook: media download ok msgId=" + msgId + " encBytes=" + enc.length);

                        String infoStr = mi.getInfoStringByMimeOrType(type);
                        XposedBridge.log("WAHook: media decrypt start info='" + infoStr + "' encBytes=" + enc.length);
                        byte[] plain = decryptWhatsAppMedia(enc, mi.mediaKeyB64, infoStr);
                        if (plain == null || plain.length == 0) { XposedBridge.log("WAHook: media decrypt failed msgId=" + msgId); return; }
                        XposedBridge.log("WAHook: media decrypt ok msgId=" + msgId + " plainBytes=" + plain.length);
                        // mark sent in cache to dedupe
                        try { MediaInfo cached = mediaCache.computeIfAbsent(msgId, k -> new MediaInfo()); cached.sent = true; } catch (Throwable ignored) {}

                        String b64 = android.util.Base64.encodeToString(plain, android.util.Base64.NO_WRAP);
                        String payload = "{" +
                                "\"type\":\"media\"," +
                                "\"docid\":" + msgId + "," +
                                (mi.mimeType != null ? "\"mime\":\"" + jsonEscape(mi.mimeType) + "\"," : "") +
                                (caption != null ? "\"caption\":\"" + jsonEscape(caption) + "\"," : "") +
                                (mi.fileLength > 0 ? "\"size\":" + mi.fileLength + "," : "") +
                                "\"data_b64\":\"" + b64 + "\"," +
                                (chatJid != null ? "\"chat_jid\":\"" + jsonEscape(chatJid) + "\"," : "") +
                                (chatName != null ? "\"chat_name\":\"" + jsonEscape(chatName) + "\"," : "") +
                                (timestamp != null ? "\"timestamp\":" + timestamp + "," : "") +
                                "\"source\":\"wahook\"" +
                                "}";
                        XposedBridge.log("WAHook: media webhook post msgId=" + msgId + " b64Len=" + b64.length() + " from=" + chatJid + " ts=" + timestamp);
                        postJsonAsync(payload);
                        try { Context ctx = getApplicationContext(); if (ctx != null) { Intent i = new Intent(ACTION); i.putExtra("payload", payload); i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES); ctx.sendBroadcast(i);} } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static class MediaInfo {
        String mediaKeyB64;
        String directPath;
        String messageUrl;
        String mimeType;
        long fileLength;
        boolean sent;
        String getInfoStringByMimeOrType(int messageType) {
            String mime = mimeType == null ? "" : mimeType;
            if (mime.startsWith("image/")) return "WhatsApp Image Keys";
            if (mime.startsWith("video/")) return "WhatsApp Video Keys";
            if (mime.startsWith("audio/")) return "WhatsApp Audio Keys";
            if (mime.contains("application")) return "WhatsApp Document Keys";
            // fallback by type
            switch (messageType) {
                case 1: return "WhatsApp Image Keys";
                case 2: return "WhatsApp Audio Keys";
                case 3: return "WhatsApp Video Keys";
                default: return "WhatsApp Document Keys";
            }
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<Long, MediaInfo> mediaCache = new java.util.concurrent.ConcurrentHashMap<>();

    private void mergeMediaFromCv(long msgId, String table, ContentValues cv) {
        try {
            MediaInfo mi = mediaCache.computeIfAbsent(msgId, k -> new MediaInfo());
            boolean changed = false;
            if (cv.containsKey("direct_path")) { String v = cv.getAsString("direct_path"); if (v != null && (mi.directPath == null || mi.directPath.isEmpty())) { mi.directPath = v; changed = true; }}
            if (cv.containsKey("message_url")) { String v = cv.getAsString("message_url"); if (v != null && (mi.messageUrl == null || mi.messageUrl.isEmpty())) { mi.messageUrl = v; changed = true; }}
            if (cv.containsKey("mime_type")) { String v = cv.getAsString("mime_type"); if (v != null && (mi.mimeType == null || mi.mimeType.isEmpty())) { mi.mimeType = v; changed = true; }}
            if (cv.containsKey("file_length")) { Long v = cv.getAsLong("file_length"); if (v != null && v > 0 && mi.fileLength == 0) { mi.fileLength = v; changed = true; }}
            // Try common key fields
            String[] keyCandidates = new String[] { "media_key", "key", "media_key_b64", "enc_media_key", "cipher_key" };
            for (String k : keyCandidates) {
                if (cv.containsKey(k)) {
                    Object val = cv.get(k);
                    if (val instanceof String) {
                        String s = (String) val;
                        if (s != null && s.length() >= 32 && (mi.mediaKeyB64 == null || mi.mediaKeyB64.isEmpty())) { mi.mediaKeyB64 = s; changed = true; }
                    } else if (val instanceof byte[]) {
                        byte[] b = (byte[]) val;
                        if (b != null && b.length >= 24 && (mi.mediaKeyB64 == null || mi.mediaKeyB64.isEmpty())) { mi.mediaKeyB64 = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP); changed = true; }
                    }
                }
            }
            // Heuristic: scan any column containing "key"
            if (mi.mediaKeyB64 == null || mi.mediaKeyB64.isEmpty()) {
                try {
                    java.util.Set<java.util.Map.Entry<String, Object>> set = cv.valueSet();
                    for (java.util.Map.Entry<String, Object> e : set) {
                        String k = e.getKey();
                        if (k == null || !k.toLowerCase().contains("key")) continue;
                        Object val = e.getValue();
                        if (val instanceof String) {
                            String s = (String) val;
                            if (s != null && s.length() >= 32) { mi.mediaKeyB64 = s; changed = true; break; }
                        } else if (val instanceof byte[]) {
                            byte[] b = (byte[]) val;
                            if (b != null && b.length >= 24) { mi.mediaKeyB64 = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP); changed = true; break; }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            XposedBridge.log("WAHook: mediaCache merge msgId=" + msgId + " table=" + table + " changed=" + changed + " hasKey=" + (mi.mediaKeyB64!=null) + " hasPath=" + ((mi.directPath!=null)||(mi.messageUrl!=null)));
        } catch (Throwable t) {
            XposedBridge.log("WAHook: mediaCache merge error " + t);
        }
    }

    private void tryDecryptFromCache(Object sqliteDbObj, long msgId) {
        try {
            MediaInfo mi = mediaCache.get(msgId);
            if (mi == null || mi.sent) return;
            boolean hasKey = mi.mediaKeyB64 != null && !mi.mediaKeyB64.isEmpty();
            boolean hasPath = (mi.directPath != null && !mi.directPath.isEmpty()) || (mi.messageUrl != null && !mi.messageUrl.isEmpty());
            if (!hasKey || !hasPath) return;
            // fetch caption
            String caption = null; Integer type = null;
            Object c = rawQueryAnyDb(sqliteDbObj, "SELECT message_type, text_data FROM message WHERE sort_id=? OR _id=? OR rowid=? LIMIT 1", new String[]{ String.valueOf(msgId), String.valueOf(msgId), String.valueOf(msgId) }, "WAHook/mediaTypeCache");
            if (c != null) {
                try {
                    Boolean mv = (Boolean) XposedHelpers.callMethod(c, "moveToFirst");
                    if (mv != null && mv) {
                        int idxT = (Integer) XposedHelpers.callMethod(c, "getColumnIndexOrThrow", "message_type");
                        int idxCap = (Integer) XposedHelpers.callMethod(c, "getColumnIndexOrThrow", "text_data");
                        type = (Integer) XposedHelpers.callMethod(c, "getInt", idxT);
                        caption = (String) XposedHelpers.callMethod(c, "getString", idxCap);
                    }
                } finally { try { XposedHelpers.callMethod(c, "close"); } catch (Throwable ignored) {} }
            }
            byte[] enc = downloadEnc(mi);
            if (enc == null || enc.length == 0) return;
            String infoStr = mi.getInfoStringByMimeOrType(type == null ? 0 : type);
            byte[] plain = decryptWhatsAppMedia(enc, mi.mediaKeyB64, infoStr);
            if (plain == null || plain.length == 0) return;
            mi.sent = true;
            String b64 = android.util.Base64.encodeToString(plain, android.util.Base64.NO_WRAP);
            String payload = "{" +
                    "\"type\":\"media\"," +
                    "\"docid\":" + msgId + "," +
                    (mi.mimeType != null ? "\"mime\":\"" + jsonEscape(mi.mimeType) + "\"," : "") +
                    (caption != null ? "\"caption\":\"" + jsonEscape(caption) + "\"," : "") +
                    (mi.fileLength > 0 ? "\"size\":" + mi.fileLength + "," : "") +
                    "\"data_b64\":\"" + b64 + "\"," +
                    "\"source\":\"wahook\"" +
                    "}";
            XposedBridge.log("WAHook: media webhook post (cache) msgId=" + msgId + " b64Len=" + b64.length());
            postJsonAsync(payload);
            try { Context ctx = getApplicationContext(); if (ctx != null) { Intent i = new Intent(ACTION); i.putExtra("payload", payload); i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES); ctx.sendBroadcast(i);} } catch (Throwable ignored) {}
        } catch (Throwable t) {
            XposedBridge.log("WAHook: tryDecryptFromCache err " + t);
        }
    }

    private MediaInfo queryMediaInfo(Object sqliteDbObj, long msgId) {
        String[] candidates = new String[] {
                // message_media is primary link
                "SELECT * FROM message_media WHERE message_row_id=?",
                // optional alternates
                "SELECT * FROM media WHERE message_row_id=?",
                "SELECT * FROM media_data WHERE message_row_id=?",
                // secrets/details may carry keys
                "SELECT * FROM message_secret WHERE message_row_id=?",
                "SELECT * FROM message_details WHERE message_row_id=?"
        };
        MediaInfo agg = new MediaInfo();
        boolean found = false;
        for (String sql : candidates) {
            XposedBridge.log("WAHook: media query try sql='" + sql + "' msgId=" + msgId);
            Object cur = rawQueryAnyDb(sqliteDbObj, sql, new String[]{ String.valueOf(msgId) }, "WAHook/mediaInfo");
            if (cur == null) continue;
            try {
                Boolean mv = (Boolean) XposedHelpers.callMethod(cur, "moveToFirst");
                if (mv != null && mv) {
                    found = true;
                    // Merge known columns
                    try { int i = (Integer) XposedHelpers.callMethod(cur, "getColumnIndex", "direct_path"); if (i>=0) { String v=(String)XposedHelpers.callMethod(cur,"getString",i); if (v!=null && (agg.directPath==null||agg.directPath.isEmpty())) agg.directPath=v; } } catch (Throwable ignored) {}
                    try { int i = (Integer) XposedHelpers.callMethod(cur, "getColumnIndex", "message_url"); if (i>=0) { String v=(String)XposedHelpers.callMethod(cur,"getString",i); if (v!=null && (agg.messageUrl==null||agg.messageUrl.isEmpty())) agg.messageUrl=v; } } catch (Throwable ignored) {}
                    try { int i = (Integer) XposedHelpers.callMethod(cur, "getColumnIndex", "mime_type"); if (i>=0) { String v=(String)XposedHelpers.callMethod(cur,"getString",i); if (v!=null && (agg.mimeType==null||agg.mimeType.isEmpty())) agg.mimeType=v; } } catch (Throwable ignored) {}
                    try {
                        int i = (Integer) XposedHelpers.callMethod(cur, "getColumnIndex", "file_length");
                        if (i>=0) {
                            try { long v = (long)(Integer) XposedHelpers.callMethod(cur, "getInt", i); if (v>0 && agg.fileLength==0) agg.fileLength=v; } catch (Throwable ignoredInt) {
                                try { long v = (Long) XposedHelpers.callMethod(cur, "getLong", i); if (v>0 && agg.fileLength==0) agg.fileLength=v; } catch (Throwable ignoredLong) {}
                            }
                        }
                    } catch (Throwable ignored) {}
                    // Try media_key in common names
                    String[] keyCols = new String[] { "media_key", "key", "media_key_b64", "enc_media_key", "cipher_key" };
                    for (String kc : keyCols) {
                        try {
                            int i = (Integer) XposedHelpers.callMethod(cur, "getColumnIndex", kc);
                            if (i >= 0) {
                                String mk = null;
                                try { mk = (String) XposedHelpers.callMethod(cur, "getString", i); } catch (Throwable ignored2) {}
                                if (mk != null && mk.length() >= 32 && (agg.mediaKeyB64==null || agg.mediaKeyB64.isEmpty())) {
                                    agg.mediaKeyB64 = mk;
                                    break;
                                }
                                try {
                                    byte[] mb = (byte[]) XposedHelpers.callMethod(cur, "getBlob", i);
                                    if (mb != null && mb.length >= 24 && (agg.mediaKeyB64==null || agg.mediaKeyB64.isEmpty())) {
                                        agg.mediaKeyB64 = android.util.Base64.encodeToString(mb, android.util.Base64.NO_WRAP);
                                        XposedBridge.log("WAHook: media_key read as BLOB len=" + mb.length + " col=" + kc);
                                        break;
                                    }
                                } catch (Throwable ignored3) {}
                            }
                        } catch (Throwable ignoredK) {}
                    }
                    // Generic scan: any column containing 'key'
                    try {
                        Integer cc = (Integer) XposedHelpers.callMethod(cur, "getColumnCount");
                        for (int idx = 0; idx < (cc==null?0:cc); idx++) {
                            String cn = (String) XposedHelpers.callMethod(cur, "getColumnName", idx);
                            if (cn == null || !cn.toLowerCase().contains("key")) continue;
                            if (agg.mediaKeyB64 != null && !agg.mediaKeyB64.isEmpty()) break;
                            try {
                                String mk = (String) XposedHelpers.callMethod(cur, "getString", idx);
                                if (mk != null && mk.length() >= 32) { agg.mediaKeyB64 = mk; break; }
                            } catch (Throwable ignoredS) {}
                            try {
                                byte[] mb = (byte[]) XposedHelpers.callMethod(cur, "getBlob", idx);
                                if (mb != null && mb.length >= 24) { agg.mediaKeyB64 = android.util.Base64.encodeToString(mb, android.util.Base64.NO_WRAP); XposedBridge.log("WAHook: media_key read generic BLOB len=" + mb.length + " col=" + cn); break; }
                            } catch (Throwable ignoredB) {}
                        }
                    } catch (Throwable ignoredCols) {}
                }
            } catch (Throwable ignored) {
            } finally { try { XposedHelpers.callMethod(cur, "close"); } catch (Throwable ignored) {} }
        }
        if (found) {
            boolean hasKey = agg.mediaKeyB64 != null && !agg.mediaKeyB64.isEmpty();
            XposedBridge.log("WAHook: media query agg msgId=" + msgId + " mime=" + agg.mimeType + " len=" + agg.fileLength + " hasKey=" + hasKey + " hasDirectPath=" + (agg.directPath!=null) + " hasMessageUrl=" + (agg.messageUrl!=null));
            return agg;
        }
        return null;
    }

    private Object safeRawQuery(Object db, String sql, String[] args) {
        try { return XposedHelpers.callMethod(db, "rawQuery", sql, args); } catch (Throwable t) { XposedBridge.log("WAHook: rawQuery fail sql='" + sql + "' err=" + t); return null; }
    }

    private Object rawQueryAnyDb(Object sqliteDbObj, String sql, String[] args, String tag) {
        Object cur = safeRawQuery(sqliteDbObj, sql, args);
        if (cur != null) { XposedBridge.log("WAHook: rawQuery[direct] ok tag=" + tag); return cur; }
        try {
            Class<?> c00h = Class.forName("X.C00H");
            Class<?> kiCls = Class.forName("X.C04280Ki");
            Object ki = c00h.getMethod("A08", Class.class).invoke(null, kiCls);
            if (ki != null) {
                Object ox = ki.getClass().getMethod("A05").invoke(ki);
                try {
                    Object oy = XposedHelpers.getObjectField(ox, "A02");
                    if (oy != null) {
                        Object c = XposedHelpers.callMethod(oy, "A0A", sql, tag, args);
                        if (c != null) { XposedBridge.log("WAHook: rawQuery[dao] ok tag=" + tag); return c; }
                    }
                } finally { try { XposedHelpers.callMethod(ox, "close"); } catch (Throwable ignored) {} }
            }
        } catch (Throwable ignored) {}
        XposedBridge.log("WAHook: rawQueryAnyDb miss tag=" + tag + " sql='" + sql + "'");
        return null;
    }

    // HKDF + decrypt
    private static byte[] hkdf(byte[] ikm, String info, int len) throws Exception {
        // HKDF-Extract(salt=32*0x00)
        byte[] salt = new byte[32];
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        // HKDF-Expand
        byte[] out = new byte[len];
        byte[] t = new byte[0];
        int pos = 0; byte ctr = 1;
        while (pos < len) {
            mac.init(new javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"));
            mac.update(t);
            mac.update(info.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            mac.update(ctr);
            t = mac.doFinal();
            int copy = Math.min(t.length, len - pos);
            System.arraycopy(t, 0, out, pos, copy);
            pos += copy;
            ctr++;
        }
        return out;
    }

    private byte[] decryptWhatsAppMedia(byte[] enc, String mediaKeyB64, String info) {
        try {
            XposedBridge.log("WAHook: media hkdf start info='" + info + "' encBytes=" + enc.length);
            byte[] mediaKey = android.util.Base64.decode(mediaKeyB64, android.util.Base64.DEFAULT);
            byte[] okm = hkdf(mediaKey, info, 80);
            byte[] iv = java.util.Arrays.copyOfRange(okm, 0, 16);
            byte[] cipherKey = java.util.Arrays.copyOfRange(okm, 16, 48);
            byte[] macKey = java.util.Arrays.copyOfRange(okm, 48, 80);
            XposedBridge.log("WAHook: media hkdf ok ivLen=" + iv.length + " cipherKeyLen=" + cipherKey.length + " macKeyLen=" + macKey.length);
            if (enc.length <= 10) { XposedBridge.log("WAHook: media enc too short for CBC/HMAC"); return null; }
            byte[] mac10 = java.util.Arrays.copyOfRange(enc, enc.length - 10, enc.length);
            byte[] ct = java.util.Arrays.copyOfRange(enc, 0, enc.length - 10);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(macKey, "HmacSHA256"));
            mac.update(iv); mac.update(ct);
            byte[] calc = java.util.Arrays.copyOf(mac.doFinal(), 10);
            if (!java.util.Arrays.equals(calc, mac10)) {
                XposedBridge.log("WAHook: media HMAC mismatch, try GCM");
                // Try GCM (assume last 16 bytes tag)
                if (enc.length <= 16) { XposedBridge.log("WAHook: media enc too short for GCM"); return null; }
                byte[] tag = java.util.Arrays.copyOfRange(enc, enc.length - 16, enc.length);
                byte[] ctb = java.util.Arrays.copyOfRange(enc, 0, enc.length - 16);
                javax.crypto.Cipher gcm = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, iv);
                gcm.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(cipherKey, "AES"), spec);
                gcm.updateAAD(new byte[0]);
                byte[] combined = new byte[ctb.length + tag.length];
                System.arraycopy(ctb, 0, combined, 0, ctb.length);
                System.arraycopy(tag, 0, combined, ctb.length, tag.length);
                byte[] out = gcm.doFinal(combined);
                XposedBridge.log("WAHook: media decrypt path=GCM outBytes=" + (out==null?0:out.length));
                return out;
            }
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(cipherKey, "AES"), new javax.crypto.spec.IvParameterSpec(iv));
            byte[] out = c.doFinal(ct);
            XposedBridge.log("WAHook: media decrypt path=CBC outBytes=" + (out==null?0:out.length));
            return out;
        } catch (Throwable t) {
            XposedBridge.log("WAHook: decrypt media error " + t);
            return null;
        }
    }

    private byte[] downloadEnc(MediaInfo mi) {
        try {
            String url = mi.messageUrl != null ? mi.messageUrl : mi.directPath;
            if (url == null) return null;
            if (url.startsWith("/")) url = "https://mmg.whatsapp.net" + url;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000); conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "WAHook/1.0");
            int code = -1; try { code = conn.getResponseCode(); } catch (Throwable ignored) {}
            XposedBridge.log("WAHook: media download start url=" + url + " code=" + code);
            java.io.InputStream is = (code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n; long total=0; long limit=20L*1024*1024; // 20MB
            while ((n = is.read(buf)) > 0) { total += n; if (total>limit) break; baos.write(buf,0,n);} 
            is.close();
            XposedBridge.log("WAHook: media download done bytes=" + total + " limitHit=" + (total>=20L*1024*1024));
            conn.disconnect();
            return baos.toByteArray();
        } catch (Throwable t) {
            XposedBridge.log("WAHook: download enc error " + t);
            return null;
        }
    }

    private String queryFtsContent(Object sqliteDbObj, long docid) {
        try {
            String[] args = new String[] { String.valueOf(docid) };
            // 1) Try message.text_data by sort_id first (works for long texts on your DB)
            try {
                Object c0 = XposedHelpers.callMethod(sqliteDbObj, "rawQuery", "SELECT text_data FROM message WHERE sort_id = ?", args);
                if (c0 != null) {
                    try {
                        Boolean moved0 = (Boolean) XposedHelpers.callMethod(c0, "moveToFirst");
                        if (moved0 != null && moved0) {
                            int idx0 = (Integer) XposedHelpers.callMethod(c0, "getColumnIndexOrThrow", "text_data");
                            String txt0 = (String) XposedHelpers.callMethod(c0, "getString", idx0);
                            XposedHelpers.callMethod(c0, "close");
                            if (txt0 != null && !txt0.isEmpty()) return txt0;
                        }
                        XposedHelpers.callMethod(c0, "close");
                    } catch (Throwable ignored) { try { XposedHelpers.callMethod(c0, "close"); } catch (Throwable ignored2) {} }
                }
            } catch (Throwable ignored) {}

            // Try via internal DAO path: X.C04280Ki -> InterfaceC16980ox -> X.C16990oy.A02 (X.C04800Mj)
            // this is heuristic and may fail silently, so return null then
            // Fallback: try rawQuery on the sqliteDbObj if available
            // 2) FTS content from message_ftsv2 (short texts often here)
            // net.sqlcipher.database.SQLiteDatabase rawQuery
            try {
                Object cursor = XposedHelpers.callMethod(sqliteDbObj, "rawQuery", "SELECT content FROM message_ftsv2 WHERE docid = ?", args);
                if (cursor != null) {
                    try {
                        Boolean moved = (Boolean) XposedHelpers.callMethod(cursor, "moveToFirst");
                        if (moved != null && moved) {
                            int idx = (Integer) XposedHelpers.callMethod(cursor, "getColumnIndexOrThrow", "content");
                            String content = (String) XposedHelpers.callMethod(cursor, "getString", idx);
                            XposedHelpers.callMethod(cursor, "close");
                            return content;
                        }
                        XposedHelpers.callMethod(cursor, "close");
                    } catch (Throwable ignored) { try { XposedHelpers.callMethod(cursor, "close"); } catch (Throwable ignored2) {} }
                }
            } catch (Throwable ignored) {}
            // 3) internal DAO via C04280Ki if available (FTS path)
            try {
                Class<?> c00h = Class.forName("X.C00H");
                Class<?> kiCls = Class.forName("X.C04280Ki");
                Object ki = c00h.getMethod("A08", Class.class).invoke(null, kiCls);
                if (ki != null) {
                    Object ox = ki.getClass().getMethod("A05").invoke(ki);
                    try {
                        Object oy = XposedHelpers.getObjectField(ox, "A02"); // X.C16990oy.A02 -> X.C04800Mj
                        if (oy != null) {
                            Object cursor3 = XposedHelpers.callMethod(oy, "A0A", "SELECT content FROM message_ftsv2 WHERE docid = ?", "WAHook/ftsPoll", args);
                            if (cursor3 != null) {
                                try {
                                    Boolean moved3 = (Boolean) XposedHelpers.callMethod(cursor3, "moveToFirst");
                                    if (moved3 != null && moved3) {
                                        int idx3 = (Integer) XposedHelpers.callMethod(cursor3, "getColumnIndexOrThrow", "content");
                                        String content3 = (String) XposedHelpers.callMethod(cursor3, "getString", idx3);
                                        XposedHelpers.callMethod(cursor3, "close");
                                        try { XposedHelpers.callMethod(ox, "close"); } catch (Throwable ignored) {}
                                        return content3;
                                    }
                                    XposedHelpers.callMethod(cursor3, "close");
                                } catch (Throwable ignored) { try { XposedHelpers.callMethod(cursor3, "close"); } catch (Throwable ignored2) {} }
                            }
                        }
                    } finally {
                        try { XposedHelpers.callMethod(ox, "close"); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}

            // 4) internal DAO for message.text_data (fallback)
            try {
                Class<?> c00h2 = Class.forName("X.C00H");
                Class<?> kiCls2 = Class.forName("X.C04280Ki");
                Object ki2 = c00h2.getMethod("A08", Class.class).invoke(null, kiCls2);
                if (ki2 != null) {
                    Object ox2 = ki2.getClass().getMethod("A05").invoke(ki2);
                    try {
                        Object oy2 = XposedHelpers.getObjectField(ox2, "A02");
                        if (oy2 != null) {
                            Object cur = XposedHelpers.callMethod(oy2, "A0A", "SELECT text_data FROM message WHERE sort_id = ?", "WAHook/msgPoll", args);
                            if (cur != null) {
                                try {
                                    Boolean mv = (Boolean) XposedHelpers.callMethod(cur, "moveToFirst");
                                    if (mv != null && mv) {
                                        int i = (Integer) XposedHelpers.callMethod(cur, "getColumnIndexOrThrow", "text_data");
                                        String t = (String) XposedHelpers.callMethod(cur, "getString", i);
                                        XposedHelpers.callMethod(cur, "close");
                                        try { XposedHelpers.callMethod(ox2, "close"); } catch (Throwable ignored) {}
                                        if (t != null && !t.isEmpty()) return t;
                                    }
                                    XposedHelpers.callMethod(cur, "close");
                                } catch (Throwable ignored) { try { XposedHelpers.callMethod(cur, "close"); } catch (Throwable ignored2) {} }
                            }
                        }
                    } finally { try { XposedHelpers.callMethod(ox2, "close"); } catch (Throwable ignored) {} }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }
    private static String buildContentValuesJson(ContentValues cv) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            Set<Map.Entry<String, Object>> set = cv.valueSet();
            boolean first = true;
            for (Map.Entry<String, Object> e : set) {
                if (!first) sb.append(',');
                first = false;
                String k = e.getKey();
                Object v = e.getValue();
                sb.append('"').append(jsonEscape(k)).append('"').append(':');
                if (v == null) {
                    sb.append("null");
                } else if (v instanceof Number || v instanceof Boolean) {
                    sb.append(String.valueOf(v));
                } else {
                    sb.append('"').append(jsonEscape(String.valueOf(v))).append('"');
                }
            }
            sb.append('}');
            return sb.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }

    private void handleCvAndPost(String type, ContentValues cv, Uri uri) {
        try {
            String table = "message"; // filtered earlier by path
            String msgKeyId = cv.getAsString("key_id");
            String msgChatJid = cv.getAsString("key_remote_jid");
            String msgRemoteResource = cv.getAsString("remote_resource");
            Integer msgFromMe = cv.getAsInteger("from_me");
            Long msgTimestamp = cv.getAsLong("timestamp");
            String msgText = cv.getAsString("data");
            // alternative columns often used
            if (msgText == null) msgText = cv.getAsString("text");
            if (msgTimestamp == null) msgTimestamp = cv.getAsLong("time");
            if (msgChatJid == null) msgChatJid = cv.getAsString("chat_row_id");
            if (msgRemoteResource == null) msgRemoteResource = cv.getAsString("sender_row_id");

            // Only send for incoming messages
            if (msgFromMe != null && msgFromMe.intValue() != 0) return;

            String payload = "{" +
                    "\"type\":\"" + type + "\"," +
                    "\"table\":\"" + table + "\"," +
                    (msgKeyId != null ? "\"msg_id\":\"" + String.valueOf(msgKeyId) + "\"," : "") +
                    (msgChatJid != null ? "\"chat_jid\":\"" + String.valueOf(msgChatJid) + "\"," : "") +
                    (msgRemoteResource != null ? "\"sender_jid\":\"" + String.valueOf(msgRemoteResource) + "\"," : "") +
                    (msgFromMe != null ? "\"from_me\":" + String.valueOf(msgFromMe) + "," : "") +
                    (msgTimestamp != null ? "\"ts\":" + String.valueOf(msgTimestamp) + "," : "") +
                    (msgText != null ? "\"text\":\"" + jsonEscape(String.valueOf(msgText)) + "\"," : "") +
                    "\"cv\":" + buildContentValuesJson(cv) + "," +
                    "\"source\":\"wahook\"" +
                    "}";

            postJsonAsync(payload);
            try {
                Context ctx = getApplicationContext();
                if (ctx != null) {
                    Intent i = new Intent(ACTION);
                    i.putExtra("payload", payload);
                    i.putExtra("s_payload", payload);
                    i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    ctx.sendBroadcast(i);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            XposedBridge.log("WAHook: handleCvAndPost err " + t.toString());
        }
    }

    private void handleFtsCvAndPost(String type, ContentValues cv, Uri uri) {
        try {
            String text = cv.getAsString("content");
            Long docid = cv.getAsLong("docid");
            Long messageRowId = cv.getAsLong("message_row_id");
            String keyId = cv.getAsString("key_id");
            String payload = "{" +
                    "\"type\":\"" + type + "\"," +
                    "\"table\":\"message_ftsv2\"," +
                    (keyId != null ? "\"msg_id\":\"" + String.valueOf(keyId) + "\"," : "") +
                    (messageRowId != null ? "\"message_row_id\":" + String.valueOf(messageRowId) + "," : "") +
                    (docid != null ? "\"docid\":" + String.valueOf(docid) + "," : "") +
                    (text != null ? "\"text\":\"" + jsonEscape(String.valueOf(text)) + "\"," : "") +
                    "\"cv\":" + buildContentValuesJson(cv) + "," +
                    "\"source\":\"wahook\"" +
                    "}";
            postJsonAsync(payload);
            try {
                Context ctx = getApplicationContext();
                if (ctx != null) {
                    Intent i = new Intent(ACTION);
                    i.putExtra("payload", payload);
                    i.putExtra("s_payload", payload);
                    i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    ctx.sendBroadcast(i);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            XposedBridge.log("WAHook: handleFtsCvAndPost err " + t.toString());
        }
    }

    private void handleSqlIfFts(String type, String sql, Object[] bindArgs) {
        if (sql == null) return;
        String lower = sql.toLowerCase();
        if (!(lower.contains("message_ftsv2") || lower.contains("fts"))) return;
        // Try to extract text argument when using INSERT/UPDATE INTO message_ftsv2(content, ...)
        String text = null;
        try {
            if (bindArgs != null && bindArgs.length > 0) {
                for (Object arg : bindArgs) {
                    if (arg instanceof CharSequence) {
                        String s = arg.toString();
                        // crude heuristic: long texts pass here; prefer longest string
                        if (text == null || s.length() > text.length()) text = s;
                    }
                }
            }
        } catch (Throwable ignored) {}
        String payload = "{" +
                "\"type\":\"sql_fts\"," +
                "\"table\":\"message_ftsv2\"," +
                (text != null ? "\"text\":\"" + jsonEscape(String.valueOf(text)) + "\"," : "") +
                "\"sql\":\"" + jsonEscape(sql) + "\"," +
                "\"source\":\"wahook\"" +
                "}";
        // do not post raw SQL FTS externally
        try {
            Context ctx = getApplicationContext();
            if (ctx != null) {
                Intent i = new Intent(ACTION);
                i.putExtra("payload", payload);
                i.putExtra("s_payload", payload);
                i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                ctx.sendBroadcast(i);
            }
        } catch (Throwable ignored) {}
    }
}
