package com.magustek.szjh.report.service.impl;

import com.magustek.szjh.basedataset.entity.IEPlanSelectValueSet;
import com.magustek.szjh.basedataset.service.IEPlanSelectValueSetService;
import com.magustek.szjh.configset.bean.IEPlanScreenItemSet;
import com.magustek.szjh.configset.service.IEPlanScreenService;
import com.magustek.szjh.plan.bean.PlanHeader;
import com.magustek.szjh.plan.bean.vo.RollPlanItemDataArchiveVO;
import com.magustek.szjh.plan.service.PlanHeaderService;
import com.magustek.szjh.plan.service.RollPlanArchiveService;
import com.magustek.szjh.report.bean.Report;
import com.magustek.szjh.report.bean.vo.DateVO;
import com.magustek.szjh.report.service.StatisticalReportService;
import com.magustek.szjh.utils.ClassUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author hexin
 */
@Slf4j
@Service("StatisticalReportService")
public class StatisticalReportServiceImpl implements StatisticalReportService {

    private IEPlanScreenService iePlanScreenService;
    private IEPlanSelectValueSetService iePlanSelectValueSetService;
    private PlanHeaderService planHeaderService;
    private RollPlanArchiveService rollPlanArchiveService;

    public StatisticalReportServiceImpl(IEPlanScreenService iePlanScreenService, IEPlanSelectValueSetService iePlanSelectValueSetService, PlanHeaderService planHeaderService, RollPlanArchiveService rollPlanArchiveService) {
        this.iePlanScreenService = iePlanScreenService;
        this.iePlanSelectValueSetService = iePlanSelectValueSetService;
        this.planHeaderService = planHeaderService;
        this.rollPlanArchiveService = rollPlanArchiveService;
    }

    //@Cacheable(value = "getOutputTaxDetailByVersion")
    @Override
    public Page<Map<String, String>> getOutputTaxDetailByVersion(Report report) throws Exception{
        Vector<Map<String, String>> detailList = new Vector<>();
        List<IEPlanSelectValueSet> selectValueSetList;
        //获取待使用取数指标集合
        String outputTax = "statisticalReport/getOutputTaxDetailByVersion";
        List<IEPlanScreenItemSet> itemListByIntfa = iePlanScreenService.getItemListByIntfa(outputTax);
        Map<String, List<IEPlanScreenItemSet>> sdvarMap = itemListByIntfa.stream().collect(Collectors.groupingBy(IEPlanScreenItemSet::getSdvar));
        if(ClassUtils.isEmpty(itemListByIntfa)){
            return new PageImpl<>(new ArrayList<>());
        }
        //获取【检索项标识】字段
        List<IEPlanScreenItemSet> serchList = itemListByIntfa.stream().filter(i -> "X".equals(i.getSerch())).collect(Collectors.toList());
        List<String> sdvarList = itemListByIntfa.stream().map(IEPlanScreenItemSet::getSdvar).collect(Collectors.toList());
        //获取【检索依据字段】为【htnum】、【htsno】的指标
        List<String> htnumSdart = itemListByIntfa.stream().filter(i->"htnum".equals(i.getRefld())).map(IEPlanScreenItemSet::getSdvar).collect(Collectors.toList());
        List<String> htsnoSdart = itemListByIntfa.stream().filter(i->"htsno".equals(i.getRefld())).map(IEPlanScreenItemSet::getSdvar).collect(Collectors.toList());
        //根据serch过滤
        if(!ClassUtils.isEmpty(serchList)){
            //根据取数指标取出数据
            selectValueSetList = iePlanSelectValueSetService.getAllByVersionAndSdvarIn(report.getVersion(), serchList.get(0).getSdvar(), sdvarList);
        }else{
            throw new Exception("屏幕配置出错，无【检索项标识】！");
        }

        if(ClassUtils.isEmpty(selectValueSetList)){
            return new PageImpl<>(new ArrayList<>());
        }

        Map<String, List<IEPlanSelectValueSet>> htnumList = selectValueSetList.stream().collect(Collectors.groupingBy(IEPlanSelectValueSet::getHtnum));
        Map<String, List<IEPlanSelectValueSet>> htsnoList = selectValueSetList.stream().collect(Collectors.groupingBy(IEPlanSelectValueSet::getHtsno));

        long start = System.currentTimeMillis();
        log.warn("开始计算");
        htnumList.entrySet().parallelStream().forEach(set->{
            Map<String, String> map = new HashMap<>(2);

            //根据htnum填充值
            set.getValue().forEach(item-> {
                if(htnumSdart.contains(item.getSdart())){
                    ClassUtils.handleDate(map, sdvarMap, item);
                }
            });
            //如果取数指标没有包含检索项，则返回
            if(!map.containsKey(serchList.get(0).getSdvar())){
                return;
            }

            //补充其他字段值
            Map<String, List<IEPlanSelectValueSet>> sdartList = htsnoList.get(set.getValue().get(0).getHtsno())
                    .stream()
                    .collect(Collectors.groupingBy(IEPlanSelectValueSet::getSdart));
            sdartList.forEach((k,v)->{
                if(!map.containsKey(k) && htsnoSdart.contains(k)){
                    ClassUtils.handleDate(map, sdvarMap, v.get(0));
                }
            });

            if(!ClassUtils.isEmpty(map)){
                map.put("htnum",set.getKey());
                detailList.add(map);
            }

        });
        log.warn("计算耗时{}秒", (System.currentTimeMillis()-start) / 1000.00);
        return ClassUtils.constructPage(report, report.filter(detailList));
    }

    @Override
    public Page<Map<String, String>> getPendingItemListByDate(DateVO dateVO){
        //获取待使用取数指标集合
        String outputTax = "statisticalReport/getPendingItemListByDate";
        Map<String, List<IEPlanScreenItemSet>> sdvarMap = iePlanScreenService
                .getItemListByIntfa(outputTax)
                .stream()
                .collect(Collectors.groupingBy(IEPlanScreenItemSet::getSdvar));
        //获取当前最新版计划
        PlanHeader planHeader = planHeaderService.getLastMRPlan();
        //找出计划中ctdtp是C，并且日期在start、end范围内的所有item及其head列表
        List<RollPlanItemDataArchiveVO> itemVOList = rollPlanArchiveService.getItemListByPlanHeaderIdAndStartEndDate(planHeader.getId(),
                dateVO.getStart().replaceAll("-", ""),
                dateVO.getEnd().replaceAll("-", ""));
        //组装selectValue数据
        Map<String, List<IEPlanSelectValueSet>> selectValueMap = iePlanSelectValueSetService
                .getAllByVersion(planHeader.getCkdate())
                .stream()
                .collect(Collectors.groupingBy(IEPlanSelectValueSet::getHtsno));
        //将所有数据打散组装map
        List<Map<String, String>> maps = ClassUtils.coverBeanToMapWithSdvarMap(itemVOList, selectValueMap, sdvarMap);

        return ClassUtils.constructPage(dateVO, maps);
    }
}
