package com.xzl.seckill;

import cn.hutool.http.HttpUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * <p>
 *
 * </p>
 *
 * @author xzl
 * @date 2021/1/6
 */
@Component
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
     * 预约
     */
    public void reservation() {

    }


    /**
     * 获取商品标题
     *
     * @return {@link String}
     */
    public String getTitle() {
        String html = HttpUtil.get("https://item.jd.com/100012043978.html");
        Document doc = Jsoup.parse(html);
        return doc.getElementsByClass("p-name").text();
    }
}
