package com.xzl.seckill;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.mail.MailUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
     * 初始化
     */
    public Seckill() {
        initConfig();
    }

    /**
     * 预约
     */
    public void reservation() {
        System.out.println(StrUtil.format("开始预约>>>>>>>>>>>>>>商品名称:{}", getTitle()));
        String url = StrUtil.format("https://yushou.jd.com/youshouinfo.action?sku={}", this.skuId);
        try {
            HttpResponse execute = get(url)
                    .header(Header.REFERER.toString(), StrUtil.format("https://item.jd.com/{}.html", this.skuId))
                    .execute();
            String redirectUrl = JSON.parseObject(execute.body()).getString("url");
            HttpResponse response = get("https:" + redirectUrl).execute();
            if (response.getStatus() == HttpURLConnection.HTTP_OK) {
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

    private void initConfig() {
        try {
            InputStream in = ResourceUtil.getStream("config.properties");
            Properties properties = new Properties();
            properties.load(in);
            this.cookie = properties.getProperty("cookie");
            this.userAgent = properties.getProperty("User-Agent");
            this.skuId = properties.getProperty("skuId");
            this.mailReceiver = properties.getProperty("mailReceiver");
        } catch (IOException e) {
            System.out.println("读取配置文件异常");
        }
    }

    /**
     * 登录
     */
    public void login() {
//TODO
    }

    /**
     * 调用我的订单接口如果未登录则需要重定向302,通过状态判断是否登录。
     *
     * @return ture / false
     */
    public boolean checkLogin() {
        Map<String, String> map = new HashMap<String, String>();
        HttpResponse execute = HttpRequest.get("https://order.jd.com/center/list.action")
                .header(Header.COOKIE.toString(), this.cookie)
                .header(Header.USER_AGENT.toString(), this.userAgent)
                .execute();
        return execute.getStatus() == HttpURLConnection.HTTP_OK;
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
}
