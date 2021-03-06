package com.esell.rtb;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * rtb管理
 *
 * @author NiuLei
 * @date 2019/11/8 14:06
 */
public final class RtbManager {
    private static final RtbManager mRtbManager = new RtbManager();
    /**
     * 基本路径
     */
    private static final String URL_BASE = "http://api6.pingxiaobao.com/";

    /**
     * 广告路径
     */
    private static final String URL_AD = URL_BASE + "rtb/subscribe.shtml";
    /**
     * 静态上报路径
     */
    private static final String URL_STATIC = URL_BASE + "t.shtm";
    /**
     * 非法参数异常格式
     */
    final String illegalArgumentFormat = "It needs to be initialized before use.the %s isEmpty";
    /**
     * 签名格式
     */
    final String signFormat = "appid=%s&appkey=%s&payload=%s&sequence=%s&timestamp=%s&uuid=%s" +
            "&version=%s";
    /**
     * 路径格式
     */
    final String urlFormat = "%s?appid=%s&sequence=%s&timestamp=%s&uuid=%s&version=%s&sign=%s";

    final String staticUrlFormat =
            "%s?sid=%s&aid=%s&mid=%s&uid=%s&ip=%s&mac=%s&lat=%s&lon=%s&tt" + "=%s";
    final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHH");
    /**
     * 屏效宝广告默认版本
     */
    final String VERSION = "1.3";
    /**
     * 公司对应唯一id
     */
    private String appId;
    /**
     * 对应key
     */
    private String appKey;
    /**
     * 设备唯一id
     */
    private String unicode;

    /**
     * 广告请求
     */
    private final IRTBRequest request = new HttpUrlConnectionImp();
    /**
     * json解析框架
     */
    final Gson gson = new Gson();

    private RtbManager() {
    }

    /**
     * 获取实例
     */
    public static RtbManager getInstance() {
        return mRtbManager;
    }

    /**
     * 获取实例
     *
     * @param appId   公司对应唯一id
     * @param appKey  对应key
     * @param unicode 设备唯一id
     */
    public final void init(String appId, String appKey, String unicode) {
        YLog.d("初始化");
        this.appId = appId;
        this.appKey = appKey;
        this.unicode = unicode;
    }

    /**
     * 检查init参数
     */
    final void checkInit() {
        if (Tools.isEmpty(appId)) {
            throw new IllegalArgumentException(String.format(illegalArgumentFormat, "appId"));
        }
        if (Tools.isEmpty(appKey)) {
            throw new IllegalArgumentException(String.format(illegalArgumentFormat, "appKey"));
        }
        if (Tools.isEmpty(unicode)) {
            throw new IllegalArgumentException(String.format(illegalArgumentFormat, "unicode"));
        }
    }

    /**
     * 屏效宝广告请求
     *
     * @param onAdListener 广告监听
     * @param rtbSlot      屏效宝广告位
     */
    public void request(final OnAdListener onAdListener, RtbSlot rtbSlot) {
        checkInit();
        if (onAdListener == null) {
            YLog.e("onAdListener == null");
            return;
        }
        if (rtbSlot == null) {
            onAdListener.onAd(Message.FAILED_UNLINK_SLOT, null);
            return;
        }
        YLog.d("请求广告" + rtbSlot);
        final long currentTimeMillis = System.currentTimeMillis();
        /*请求类*/
        RtbRequestModel rtbRequestBean = new RtbRequestModel(Tools.getLocalIpAddress(),
                rtbSlot.quantity, rtbSlot.pxbSlotId, rtbSlot.type, unicode);
        /*对象转json格式*/
        final String payload = gson.toJson(rtbRequestBean);
        /*签名格式字符串*/
        final String signFormatStr = String.format(signFormat, appId, appKey, payload,
                currentTimeMillis, currentTimeMillis, unicode, VERSION);
        /*最终签名*/
        final String sign = Tools.md5Hex(signFormatStr);
        /*请求路径*/
        final String url = String.format(urlFormat, URL_AD, appId, currentTimeMillis,
                currentTimeMillis, unicode, VERSION, sign);
        HashMap<String, String> params = new HashMap<>();
        params.put("payload", payload);
        request.postOnWorkThread(url, params, new IRTBRequest.Callback() {
            @Override
            public void onFinish(Message message, String response) {
                if (Message.SUCCESS.equals(message)) {
                    Result<List<RtbAD>> result = null;
                    try {
                        result = gson.fromJson(response, new TypeToken<Result<List<RtbAD>>>() {
                        }.getType());
                    } catch (JsonSyntaxException e) {
                        e.printStackTrace();
                        onAdListener.onAd(new Message("JsonSyntaxException".hashCode(),
                                Tools.throwable2String(e)), null);
                        return;
                    }
                    if (result == null) {
                        onAdListener.onAd(new Message("result == null".hashCode(),
                                "result == " + "null"), null);
                        return;
                    }
                    if (result.isSuccess()) {
                        onAdListener.onAd(message, result.getPayload());
                        return;
                    }
                }
                onAdListener.onAd(message, null);
            }
        });
    }

    /**
     * 屏效宝广告请求
     *
     * @param onAdListener 广告监听
     * @param rtbSlots     多个广告位
     */
    public void request(final OnAdListener onAdListener, RtbSlot... rtbSlots) {
        if (onAdListener == null) {
            YLog.e("onAdListener == null");
            return;
        }
        if (rtbSlots == null || rtbSlots.length == 0) {
            onAdListener.onAd(Message.FAILED_UNLINK_SLOT, null);
            return;
        }
        YLog.d("批量请求广告" + rtbSlots);
        final CountDownLatch countDownLatch = new CountDownLatch(rtbSlots.length);
        final List<RtbAD> allAdList = Collections.synchronizedList(new LinkedList<RtbAD>());
        Tools.pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    countDownLatch.await(IRTBRequest.TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    onAdListener.onAd(Message.SUCCESS, allAdList);
                }
            }
        });
        for (final RtbSlot rtbSlot : rtbSlots) {
            request(new OnAdListener() {
                @Override
                public void onAd(Message message, List<RtbAD> adList) {
                    if (Message.SUCCESS.equals(message) && adList != null) {
                        allAdList.addAll(adList);
                    } else {
                        YLog.d(rtbSlot.toString() + " " + message.toString());
                    }
                    countDownLatch.countDown();
                }
            }, rtbSlot);
        }
    }

    /**
     * 静态上报
     *
     * @param slotId 广告位id
     * @param adId   广告id
     */
    public void staticReport(String slotId, String adId, IRTBRequest.Callback callback) {
        staticReport(slotId, adId, 0.0D, 0.0D, callback);
    }

    /**
     * 静态上报
     *
     * @param slotId   广告位id
     * @param adId     广告id
     * @param lat      经纬度
     * @param lon      经纬度
     * @param callback 回调
     */
    public void staticReport(String slotId, String adId, double lat, double lon,
                             IRTBRequest.Callback callback) {
        checkInit();
        String mid = "";
        String uid = unicode;
        String ip = Tools.getLocalIpAddress();
        List<String> macList = null;
        try {
            macList = Tools.getMacList();
        } catch (Exception e) {
            e.printStackTrace();
        }
        String mac = macList == null || macList.isEmpty() ? "" : macList.get(0);
        String tt = simpleDateFormat.format(new Date());
        String url = String.format(staticUrlFormat, URL_STATIC, slotId, adId, mid, uid, ip, mac,
                lat, lon, tt);
        request.postOnWorkThread(url, callback);
    }

    /**
     * 动态上报
     *
     * @param trackUrl 上报路径
     */
    public void dynamicReport(String trackUrl, IRTBRequest.Callback callback) {
        checkInit();
        request.postOnWorkThread(trackUrl, callback);
    }
}
