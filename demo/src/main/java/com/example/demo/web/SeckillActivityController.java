package com.example.demo.web;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson.JSON;
import com.example.demo.db.dao.OrderDao;
import com.example.demo.db.dao.SeckillActivityDao;
import com.example.demo.db.dao.SeckillCommodityDao;
import com.example.demo.db.po.Order;
import com.example.demo.db.po.SeckillActivity;
import com.example.demo.db.po.SeckillCommodity;
import com.example.demo.services.SeckillActivityService;
import com.example.demo.util.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class SeckillActivityController {
    @RequestMapping("/addSeckillActivity")
    public String addSeckillActivity() {
        return "add_activity";
    }

    @Autowired
    private SeckillActivityDao seckillActivityDao;
    @Autowired
    private OrderDao orderDao;
    @Resource
    private RedisService redisService;

    @ResponseBody
    @RequestMapping("/addSeckillActivityAction")
    public String addSeckillActivityAction(
            @RequestParam("name") String name,
            @RequestParam("commodityId") long commodityId,
            @RequestParam("seckillPrice") BigDecimal seckillPrice,
            @RequestParam("oldPrice") BigDecimal oldPrice,
            @RequestParam("seckillNumber") long seckillNumber,
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime,
            Map<String, Object> resultMap
    ) throws ParseException {
            startTime = startTime.substring(0, 10) + startTime.substring(11);
            endTime = endTime.substring(0, 10) + endTime.substring(11);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-ddhh:mm");
            SeckillActivity seckillActivity = new SeckillActivity();
            seckillActivity.setName(name);
            seckillActivity.setCommodityId(commodityId);
            seckillActivity.setSeckillPrice(seckillPrice);
            seckillActivity.setOldPrice(oldPrice);
            seckillActivity.setTotalStock(seckillNumber);
            seckillActivity.setAvailableStock(new Integer("" + seckillNumber));
            seckillActivity.setLockStock(0L);
            seckillActivity.setActivityStatus(1);
            seckillActivity.setStartTime(format.parse(startTime));
            seckillActivity.setEndTime(format.parse(endTime));
            seckillActivityDao.inertSeckillActivity(seckillActivity);
            resultMap.put("seckillActivity", seckillActivity);
            return "add_success";
        }

    @RequestMapping("/seckills")
    public String activityList(Map<String, Object> resultMap) {
        try (Entry entry = SphU.entry("seckills")) {
            List<SeckillActivity> seckillActivities =
                    seckillActivityDao.querySeckillActivitysByStatus(1);
            resultMap.put("seckillActivities", seckillActivities);
            return "seckill_activity";
        } catch (BlockException ex) {
            log.error("???????????????????????????????????? "+ex.toString());
            return "wait";
        }
    }


    @Autowired
    private SeckillCommodityDao seckillCommodityDao;
    @RequestMapping("/item/{seckillActivityId}")
    public String itemPage(Map<String, Object> resultMap, @PathVariable long seckillActivityId) {
        SeckillActivity seckillActivity;
        SeckillCommodity seckillCommodity;
        String seckillActivityInfo = redisService.getValue("seckillActivity:" + seckillActivityId);
        if (StringUtils.isNotEmpty(seckillActivityInfo)) {
            log.info("redis????????????:" + seckillActivityInfo);
            seckillActivity = JSON.parseObject(seckillActivityInfo, SeckillActivity.class);
        } else {
            seckillActivity = seckillActivityDao.querySeckillActivityById(seckillActivityId);
        }
        String seckillCommodityInfo = redisService.getValue("seckillCommodity:" + seckillActivity.getCommodityId());
        if (StringUtils.isNotEmpty(seckillCommodityInfo)) {
            log.info("redis????????????:" + seckillCommodityInfo);
            seckillCommodity = JSON.parseObject(seckillActivityInfo,
                    SeckillCommodity.class);
        } else {
            seckillCommodity = seckillCommodityDao.querySeckillCommodityById(seckillActivity.getCommodityId());
        }
        resultMap.put("seckillActivity", seckillActivity);
        resultMap.put("seckillCommodity", seckillCommodity);
        resultMap.put("seckillPrice", seckillActivity.getSeckillPrice());
        resultMap.put("oldPrice", seckillActivity.getOldPrice());
        resultMap.put("commodityId", seckillActivity.getCommodityId());
        resultMap.put("commodityName", seckillCommodity.getCommodityName());
        resultMap.put("commodityDesc", seckillCommodity.getCommodityDesc());
        return "seckill_item";
    }


    @Autowired
    SeckillActivityService seckillActivityService;
    /**
     * ??????????????????
     * @param userId
     * @param seckillActivityId
     * @return
     */
    @RequestMapping("/seckill/buy/{userId}/{seckillActivityId}")
    public ModelAndView seckillCommodity(@PathVariable long userId,
                                         @PathVariable long seckillActivityId) {
        boolean stockValidateResult = false;
        ModelAndView modelAndView = new ModelAndView();
        try {
            /*
             * ????????????????????????????????????
             */
            if (redisService.isInLimitMember(seckillActivityId, userId)) {
            //???????????????????????????????????????????????????
                modelAndView.addObject("resultInfo", "???????????????????????????????????????");
                modelAndView.setViewName("seckill_result");
                return modelAndView;
            }

            /*
             * ??????????????????????????????
             */
            stockValidateResult =
                    seckillActivityService.seckillStockValidator(seckillActivityId);
            if (stockValidateResult) {
                Order order =
                        seckillActivityService.createOrder(seckillActivityId, userId);
                modelAndView.addObject("resultInfo","???????????????????????????????????????ID???" + order.getOrderNo());
                modelAndView.addObject("orderNo",order.getOrderNo());
                //??????????????????????????????
                redisService.addLimitMember(seckillActivityId, userId);
            } else {
                modelAndView.addObject("resultInfo","??????????????????????????????");
            }
        } catch (Exception e) {
            log.error("??????????????????" + e.toString());
            modelAndView.addObject("resultInfo","????????????");
        }
        modelAndView.setViewName("seckill_result");
        return modelAndView;
    }

    /**
     * ????????????
     * @param orderNo
     * @return
     */
    @RequestMapping("/seckill/orderQuery/{orderNo}")
    public ModelAndView orderQuery(@PathVariable String orderNo) {
        log.info("???????????????????????????" + orderNo);
        Order order = orderDao.queryOrder(orderNo);
        ModelAndView modelAndView = new ModelAndView();
        if (order != null) {
            modelAndView.setViewName("order");
            modelAndView.addObject("order", order);
            SeckillActivity seckillActivity =
                    seckillActivityDao.querySeckillActivityById(order.getSeckillActivityId());
            modelAndView.addObject("seckillActivity", seckillActivity);
        } else {
            modelAndView.setViewName("order_wait");
        }
        return modelAndView;
    }

    /**
     * ????????????
     * @return
     */
    @RequestMapping("/seckill/payOrder/{orderNo}")
    public String payOrder(@PathVariable String orderNo) throws Exception {
        seckillActivityService.payOrderProcess(orderNo);
        return "redirect:/seckill/orderQuery/" + orderNo;
    }

    /**
     * ?????????????????????????????????
     * @return
     */
    @ResponseBody
    @RequestMapping("/seckill/getSystemTime")
    public String getSystemTime() {
        //??????????????????
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // new Date()???????????????????????????
        String date = df.format(new Date());
        return date;
    }




}