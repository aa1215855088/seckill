package com.xzl.seckill;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.extra.mail.MailUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * <p>
 *
 * </p>
 *
 * @author xzl
 * @date 2021/1/6
 */
public class Seckill {

    /**
     * 后台任务线程
     */
    ScheduledExecutorService taskThread = Executors.newSingleThreadScheduledExecutor();

    /**
     * 线程池
     */
    ExecutorService executorService = ThreadUtil.newExecutor();

    /**
     * 商品标题
     */
    private String title;

    /**
     * 商品ID
     */
    private String skuId;

    /**
     * 用户cookie
     */
    private String cookie;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 邮件接收人
     */
    private String mailReceiver;

    /**
     * 抢购线程数
     */
    private int workCount;

    /**
     * 商品数量
     */
    private int num;

    /**
     * 预购cron
     */
    private String reservationCron;

    /**
     * cookie cron
     */
    private String cookieCron;

    /**
     * 每天抢购时间
     */
    private String buyTime;

    private String fp;

    private String eid;

    /**
     * 初始化
     */
    public Seckill() {
        initConfig();

        //秒杀任务
        executorService.execute(() -> {
            for (int i = 0; i < workCount; i++) {
                executorService.execute(this::seckill);
            }
        });

        //预约任务
        CronUtil.schedule(this.reservationCron, (Task) () -> {
            if (checkLogin()) {
                reservation();
            }
        });

        //cookie检查任务
        CronUtil.schedule(this.cookieCron, (Task) () -> {
            System.out.println(">>>>>>>>>>>>>>>>>>>>开始检测cookie是否失效<<<<<<<<<<<<<<<<<<<<<<<");
            if (!checkLogin()) {
                MailUtil.send(mailReceiver, "cookie过期", "请手动获取", false);
                System.out.println(">>>>>>>>>>>>>>>>>>>>cookie失效系统自动退出<<<<<<<<<<<<<<<<<<<<<<<");
                close();
            }
        });

        //钩子函数
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            taskThread.shutdown();
            CronUtil.stop();
            executorService.shutdown();
            System.out.println("系统退出!!!");
        }));

        // 支持秒级别定时任务
        CronUtil.setMatchSecond(true);
        CronUtil.start();
    }


    private void initConfig() {
        try {
            InputStream in = ResourceUtil.getStream("config.properties");
            Properties properties = new Properties();
            properties.load(in);
            this.cookie = properties.getProperty("cookie");
            this.userAgent = properties.getProperty("User-Agent");
            this.skuId = properties.getProperty("skuId");
            this.mailReceiver = properties.getProperty("mailReceiver");
            this.cookieCron = properties.getProperty("cookieCron");
            this.buyTime = properties.getProperty("buyTime");
            this.reservationCron = properties.getProperty("reservationCron");
            this.fp = properties.getProperty("fp");
            this.eid = properties.getProperty("eid");
            this.workCount = Integer.parseInt(properties.getProperty("work_count", "5"));
            this.num = Integer.parseInt(properties.getProperty("num", "2"));
        } catch (IOException e) {
            System.out.println("读取配置文件异常");
        }
    }

    /**
     * 获取jd服务器时间
     *
     * @return 时间戳
     */
    public long getJdServerTime() {
        String json = HttpUtil.get("https://a.jd.com//ajax/queryServerData.html");
        return JSON.parseObject(json).getLong("serverTime");
    }

    /**
     * 预约
     */
    public void reservation() {
        System.out.println(StrUtil.format("开始预约>>>>>>>>>>>>>>商品名称:{}", getTitle()));
        String url = "https://yushou.jd.com/youshouinfo.action";
        try {
            HttpResponse execute = get(url)
                    .header(Header.REFERER.toString(), StrUtil.format("https://item.jd.com/{}.html", this.skuId))
                    .form("sku", this.skuId)
                    .execute();
            String redirectUrl = JSON.parseObject(execute.body()).getString("url");
            HttpResponse response = get("https:" + redirectUrl).execute();
            if (response.getStatus() == HttpStatus.HTTP_OK) {
                Document doc = Jsoup.parse(response.body());
                System.out.println("预约成功>>>>>>>>" + doc.getElementsByClass("bd-right-result").text());
                MailUtil.send(this.mailReceiver, "预约成功!!!", "", false);
                return;
            }
        } catch (HttpException e) {
            System.out.println("预约失败!!!");
        }
        MailUtil.send(this.mailReceiver, "预约失败!!!", "请手动预约", false);
    }

    /**
     * 秒杀抢购
     */
    public void seckill() {
        System.out.println(StrUtil.format("开始抢购>>>>>>>>>>>>>>商品名称:{}", getTitle()));
        while (true) {
            long timeDifference = getJdServerTime() - getBuyTime();
            //默认抢60s
            if (timeDifference >= 0 && timeDifference <= 60 * 1000) {
                //开始秒杀
                try {
                    startSeckill();
                } catch (Exception e) {
                    System.out.println("秒杀失败!开始重试.....");
                }
            } else if (timeDifference < 0) {
                System.out.println("活动暂未开始,距离活动开始还剩[" + Math.abs(timeDifference / 1000) + "]s");
                ThreadUtil.sleep(Math.abs(timeDifference));
            } else {
                System.out.println("今日活动已结束");
                //计算距离第二天活动开始相差时间
                DateTime offset = DateUtil.offset(new Date(getBuyTime()), DateField.DAY_OF_YEAR, 1);
                ThreadUtil.sleep(offset.getTime() - timeDifference);
            }
        }
    }

    /**
     * 开始秒杀-----------
     */
    public void startSeckill() {
        String seckillUrl = getSeckillUrl();
        if (StrUtil.isBlank(seckillUrl)) {
            return;
        }
        System.out.println("访问商品抢购链接");
        HttpResponse execute = get(seckillUrl)
                .header(Header.HOST, "marathon.jd.com")
                .header(Header.REFERER, StrUtil.format("https://item.jd.com/{}.html", this.skuId))
                .setFollowRedirects(true)
                .execute();
        //请求抢购结算结算
        requestSeckillCheckOutPage();
        //提交抢购订单
        submitSeckillOrder();
    }

    /**
     * 提交seckill订单
     */
    public void submitSeckillOrder() {
        String url = "https://marathon.jd.com/seckillnew/orderService/pc/submitOrder.action";
        try {
            Map<String, Object> data = getSeckillOrderData();
            System.out.println(JSON.toJSONString(data));
            HttpResponse execute = post(url)
                    .header(Header.HOST, "marathon.jd.com")
                    .header(Header.REFERER, StrUtil.format("https://marathon.jd.com/seckill/seckill" +
                            ".action?skuId={}&num={}&rid={}", this.skuId, this.num, DateUtil.current()))
                    .form(data)
                    .execute();
            JSONObject result = JSON.parseObject(execute.body());
            if (result.getBoolean("success")) {
                System.out.println("抢购成功");
                MailUtil.send(this.mailReceiver, "抢购成功!!!", getTitle() + "抢购成功,请赶快结算订单", false);
            }
            System.out.println(result);
        } catch (HttpException e) {
            System.out.println("抢购失败!!!");
        }
    }

    /**
     * 生成秒杀提交订单所需参数
     *
     * @return {@link Map<String, Object>}
     */
    public Map<String, Object> getSeckillOrderData() {
        JSONObject info = getSeckillInitInfo();
        System.out.println("生成秒杀提交订单所需参数");
        Map<String, Object> data = Maps.newHashMap();
        JSONObject address = info.getJSONArray("addressList").getJSONObject(0);
        JSONObject invoiceInfo = info.getJSONObject("invoiceInfo");
        data.put("skuId", this.skuId);
        data.put("num", this.num);
        data.put("addressId", address.get("id"));
        data.put("yuShou", true);
        data.put("isModifyAddress", false);
        data.put("name", address.getString("name"));
        data.put("provinceId", address.get("provinceId"));
        data.put("cityId", address.get("cityId"));
        data.put("countyId", address.get("countyId"));
        data.put("townId", address.get("townId"));
        data.put("addressDetail", address.get("addressDetail"));
        data.put("mobile", address.get("mobile"));
        data.put("mobileKey", address.get("mobileKey"));
        data.put("email", "");
        data.put("postCode", "");
        if (Objects.nonNull(invoiceInfo)) {
            data.put("invoiceTitle", invoiceInfo.getOrDefault("invoiceTitle", -1));
            data.put("invoiceCompanyName", "");
            data.put("invoiceContent", invoiceInfo.getOrDefault("invoiceContent", 1));
            data.put("invoiceTaxpayerNO", "");
            data.put("invoiceEmail", "");
            data.put("invoicePhone", invoiceInfo.getOrDefault("invoicePhone", ""));
            data.put("invoicePhoneKey", invoiceInfo.getOrDefault("invoicePhoneKey", ""));
            data.put("invoice", true);
        }
        data.put("invoice", false);
        data.put("password", "");
        data.put("codTimeType", 3);
        data.put("paymentType", 4);
        data.put("areaCode", "");
        data.put("overseas", 0);
        data.put("phone", "");
        data.put("eid", this.eid);
        data.put("fp", this.fp);
        data.put("token", info.get("token"));
        data.put("pru", "");
        System.out.println(StrUtil.format("抢购参数:{}", JSON.toJSONString(data)));
        return data;
    }

    /**
     * 获取秒杀初始化信息（包括：地址，发票，token）
     *
     * @return JSONObject
     */
    public JSONObject getSeckillInitInfo() {
        System.out.println("获取秒杀初始化信息....");
        String url = "https://marathon.jd.com/seckillnew/orderService/pc/init.action";
        Map<String, Object> data = Maps.newHashMap();
        data.put("sku", this.skuId);
        data.put("num", this.num);
        data.put("isModifyAddress", false);
        HttpResponse execute = post(url)
                .header(Header.REFERER, "marathon.jd.com")
                .form(data)
                .execute();
        return JSON.parseObject(execute.body());
    }

    /**
     * 请求seckill结算页面
     */
    private void requestSeckillCheckOutPage() {
        System.out.println(">>>>>>>>>>>访问订单结算页面<<<<<<<<<<");
        String url = "https://marathon.jd.com/seckill/seckill.action";
        Map<String, Object> param = Maps.newHashMap();
        param.put("num", this.num);
        param.put("skuId", this.skuId);
        param.put("rid", DateUtil.current());
        HttpResponse execute = get(url)
                .header(Header.HOST, "marathon.jd.com")
                .header(Header.REFERER, StrUtil.format("https://item.jd.com/{}.html", this.skuId))
                .form(param)
                .execute();
    }


    /**
     * 获取商品的抢购链接
     * 点击"抢购"按钮后，会有两次302跳转，最后到达订单结算页面
     * 这里返回第一次跳转后的页面url，作为商品的抢购链接
     *
     * @return {@link String}
     */
    private String getSeckillUrl() {
        String url = "https://itemko.jd.com/itemShowBtn";
        Map<String, Object> param = Maps.newHashMap();
        param.put("callback", StrUtil.format("jQuery{}", RandomUtil.randomInt(1000000, 9999999)));
        param.put("skuId", this.skuId);
        param.put("from", "pc");
        param.put("_", DateUtil.current());
        HttpResponse execute = get(url)
                .header(Header.HOST, "itemko.jd.com")
                .header(Header.REFERER, StrUtil.format("https://item.jd.com/{}.html", this.skuId))
                .form(param).execute();
        String jsonStr = parseJson(execute.body());
        String routerUrl = JSON.parseObject(jsonStr).getString("url");
        if (StrUtil.isNotBlank(routerUrl)) {
            String result = HttpUtil.get("https:" + routerUrl);
            String seckillUrl = result
                    .replace("divide", "marathon")
                    .replace("user_routing", "captcha.html");
            System.out.println(StrUtil.format("获取抢购链接成功:{}", seckillUrl));
            return seckillUrl;
        }
        ThreadUtil.sleep(RandomUtil.randomInt(100, 300));
        throw new RuntimeException("抢购链接获取失败，稍后自动重试");
    }

    /**
     * 解析json
     *
     * @param body body
     * @return {@link String}
     */
    private String parseJson(String body) {
        String subAfter = StrUtil.subAfter(body, "{", false);
        return "{" + StrUtil.subBefore(subAfter, "}", false) + "}";
    }

    /**
     * 带cookie的get请求
     *
     * @param url 网址
     * @return {@link HttpRequest}
     */
    private HttpRequest get(String url) {
        return HttpRequest.get(url)
                .header(Header.COOKIE.toString(), this.cookie)
                .header(Header.USER_AGENT.toString(), this.userAgent);
    }

    /**
     * 带cookie的post请求
     *
     * @param url 网址
     * @return {@link HttpRequest}
     */
    private HttpRequest post(String url) {
        return HttpRequest.post(url)
                .header(Header.COOKIE.toString(), this.cookie)
                .header(Header.USER_AGENT.toString(), this.userAgent);
    }

    /**
     * 登录
     */
    public void login() {
//TODO
    }

    /**
     * 调用我的订单接口如果未登录则需要重定向(code=302),通过状态判断是否登录。
     *
     * @return ture / false
     */
    public boolean checkLogin() {
        Map<String, String> map = new HashMap<String, String>();
        HttpResponse execute = HttpRequest.get("https://order.jd.com/center/list.action")
                .header(Header.COOKIE.toString(), this.cookie)
                .header(Header.USER_AGENT.toString(), this.userAgent)
                .execute();
        return execute.getStatus() == HttpStatus.HTTP_OK;
    }


    private void generateQrCode() {
//TODO
    }

    /**
     * 获取商品标题
     *
     * @return {@link String}
     */
    public String getTitle() {
        String html = HttpUtil.get(StrUtil.format("https://item.jd.com/{}.html", this.skuId));
        Document doc = Jsoup.parse(html);
        return doc.getElementsByClass("p-name").text();
    }

    /**
     * 获取抢购时间戳
     *
     * @return long
     */
    public long getBuyTime() {
        String[] times = StrUtil.split(buyTime, ":");
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(times[0]));
        time.set(Calendar.MINUTE, Integer.parseInt(times[1]));
        time.set(Calendar.SECOND, Integer.parseInt(times[2]));
        time.set(Calendar.MILLISECOND, Integer.parseInt(times[3]));
        return time.getTime().getTime();
    }


    /**
     * 关机
     */
    private void close() {
        System.exit(0);
    }

}
