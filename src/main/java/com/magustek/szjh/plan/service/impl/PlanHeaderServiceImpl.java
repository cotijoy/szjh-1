package com.magustek.szjh.plan.service.impl;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Strings;
import com.magustek.szjh.basedataset.entity.DmCalcStatistics;
import com.magustek.szjh.basedataset.entity.IEPlanSelectValueSet;
import com.magustek.szjh.basedataset.service.DmCalcStatisticsService;
import com.magustek.szjh.basedataset.service.IEPlanSelectValueSetService;
import com.magustek.szjh.configset.bean.IEPlanBusinessHeadSet;
import com.magustek.szjh.configset.bean.IEPlanBusinessItemSet;
import com.magustek.szjh.configset.bean.IEPlanDimensionSet;
import com.magustek.szjh.configset.bean.OrganizationSet;
import com.magustek.szjh.configset.bean.vo.IEPlanReportHeadVO;
import com.magustek.szjh.configset.service.*;
import com.magustek.szjh.plan.bean.*;
import com.magustek.szjh.plan.bean.vo.PlanHeaderVO;
import com.magustek.szjh.plan.dao.PlanHeaderDAO;
import com.magustek.szjh.plan.dao.PlanLayoutDAO;
import com.magustek.szjh.plan.service.PlanHeaderService;
import com.magustek.szjh.plan.service.PlanItemService;
import com.magustek.szjh.plan.service.RollPlanArchiveService;
import com.magustek.szjh.plan.utils.WearsType;
import com.magustek.szjh.utils.ClassUtils;
import com.magustek.szjh.utils.ContextUtils;
import com.magustek.szjh.utils.KeyValueBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service("PlanHeaderService")
public class PlanHeaderServiceImpl implements PlanHeaderService {

    private PlanHeaderDAO planHeaderDAO;
    private PlanItemService planItemService;
    private OrganizationSetService organizationSetService;
    private ConfigDataSourceSetService configDataSourceSetService;
    private IEPlanReportHeadSetService iePlanReportHeadSetService;
    private PlanLayoutDAO planLayoutDAO;
    private RollPlanArchiveService rollPlanArchiveService;
    private IEPlanSelectValueSetService iePlanSelectValueSetService;
    private IEPlanBusinessHeadSetService iePlanBusinessHeadSetService;
    private IEPlanBusinessItemSetService iePlanBusinessItemSetService;
    private DmCalcStatisticsService dmCalcStatisticsService;

    public PlanHeaderServiceImpl(PlanHeaderDAO planHeaderDAO,
                                 PlanItemService planItemService,
                                 OrganizationSetService organizationSetService,
                                 ConfigDataSourceSetService configDataSourceSetService,
                                 IEPlanReportHeadSetService iePlanReportHeadSetService,
                                 PlanLayoutDAO planLayoutDAO,
                                 RollPlanArchiveService rollPlanArchiveService,
                                 IEPlanSelectValueSetService iePlanSelectValueSetService, IEPlanBusinessHeadSetService iePlanBusinessHeadSetService, IEPlanBusinessItemSetService iePlanBusinessItemSetService, DmCalcStatisticsService dmCalcStatisticsService) {
        this.planHeaderDAO = planHeaderDAO;
        this.planItemService = planItemService;
        this.organizationSetService = organizationSetService;
        this.configDataSourceSetService = configDataSourceSetService;
        this.iePlanReportHeadSetService = iePlanReportHeadSetService;
        this.planLayoutDAO = planLayoutDAO;
        this.rollPlanArchiveService = rollPlanArchiveService;
        this.iePlanSelectValueSetService = iePlanSelectValueSetService;
        this.iePlanBusinessHeadSetService = iePlanBusinessHeadSetService;
        this.iePlanBusinessItemSetService = iePlanBusinessItemSetService;
        this.dmCalcStatisticsService = dmCalcStatisticsService;
    }

    @Override
    public PlanHeader save(PlanHeader header) throws Exception{
        if(Strings.isNullOrEmpty(header.getBukrs())){
            header.setBukrs(ContextUtils.getCompany().getOrgcode());
            //公司报表
            if(IEPlanDimensionSet.DM_Company.equals(header.getRporg())){
                header.setOrgval(ContextUtils.getCompany().getOrgcode());
            }
            //部门报表
            if(IEPlanDimensionSet.DM_Department.equals(header.getRporg())){
                header.setOrgval(ContextUtils.getCompany().getDeptcode());
            }
        }
        //新增计划，初始化相关数据。
        if(ClassUtils.isEmpty(header.getId())){
            header.setStonr("10");//如果没有ID，默认初始值为10-创建
            header.setBsta("J01");//如果没有ID，默认初始值为J01
            //保存抬头
            header = planHeaderDAO.save(header);
            //获取计划布局配置
            IEPlanReportHeadVO config = iePlanReportHeadSetService.getReportConfigByBukrs(header.getBukrs(), header.getRptyp(), header.getRporg(), header.getJhval());
            //初始化明细数据
            List<PlanItem> itemList = planItemService.initItemDataByConfig(config, header.getId());
            //如果是月报，需要复制数据到【roll_plan_head_data_archive】、【roll_plan_item_data_archive】表，并且将统计数据存入其中
            if("MR".equals(header.getRptyp())){
                planItemService.initCalcData(itemList, config, header);
            }

            //计算T800（小计）
            calcT800(itemList);

            //保存明细数据
            planItemService.save(itemList);
            //保存布局数据
            PlanLayout layout = new PlanLayout();
            layout.setHeaderId(header.getId());
            layout.setLayout(config.toJson());
            planLayoutDAO.save(layout);
        }else{
            PlanHeader old = planHeaderDAO.findOne(header.getId());
            if(old != null){
                header.copyCreate(old);
            }
            header = planHeaderDAO.save(header);
        }
        return header;
    }

    @Transactional
    @Override
    public PlanHeader delete(PlanHeader header) {
        Assert.isTrue(!ClassUtils.isEmpty(header.getId()), "计划ID不得为空");
        PlanHeader one = planHeaderDAO.findOne(header.getId());
        //级联删除
        planItemService.deleteByHeaderId(header.getId());
        planHeaderDAO.delete(header.getId());
        planLayoutDAO.deleteAllByHeaderId(header.getId());

        if("MR".equals(one.getRptyp())){
            //月报要删除滚动计划归档数据
            rollPlanArchiveService.deleteData(one);
        }

        return header;
    }

    @Override
    public PlanHeader getById(PlanHeader header){
        Assert.isTrue(!ClassUtils.isEmpty(header.getId()), "计划ID不得为空");
        header = planHeaderDAO.findOne(header.getId());

        PlanHeaderVO vo = coverToVO(header);

        Map<String, BigDecimal> zbval = planItemService.getZBValByHeaderId(header.getId());
        ArrayList<KeyValueBean> keyValueBeans = KeyValueBean.paresMap(zbval);
        vo.setZbList(keyValueBeans);
        return vo;
    }

    @SuppressWarnings("unused")
    @Override
    public Page<Map<String, String>> getListByBukrs(PlanHeaderVO vo, Pageable pageable) throws Exception{
        Page<PlanHeader> page;
        switch (vo.getRporg()) {
            case IEPlanDimensionSet.DM_Company:
                page = planHeaderDAO.findAllByBukrsAndOrgvalAndRptypOrderByIdDesc(ContextUtils.getCompany().getOrgcode(), ContextUtils.getCompany().getOrgcode(), vo.getRptyp(), pageable);
                break;
            case IEPlanDimensionSet.DM_Department:
                page = planHeaderDAO.findAllByBukrsAndOrgvalAndRptypOrderByIdDesc(ContextUtils.getCompany().getOrgcode(), ContextUtils.getCompany().getDeptcode(), vo.getRptyp(), pageable);
                break;
            default:
                throw new Exception("请指定公司报表还是部门报表！");
        }

        List<PlanHeader> content = page.getContent();
        List<Map<String, String>> voList = new ArrayList<>(content.size());

        for(PlanHeader header : content){
            PlanHeaderVO pvo = coverToVO(header);
            pvo.setZbList(planItemService.getZbList(pvo.getId(), header.getRptyp()));
            voList.add(ClassUtils.coverToMapJson(pvo,"zbList", pvo.getUnit()));
        }
        page.getContent();
        return new PageImpl<>(voList, pageable, page.getTotalElements());
    }

    @Override
    public IEPlanReportHeadVO getLayoutByHeaderId(Long headerId) {
        return JSON.parseObject(planLayoutDAO.findTopByHeaderId(headerId).getLayout(), IEPlanReportHeadVO.class);
    }

    @Override
    public List<Map<String, String>> getHtsnoList(String zbart, String dmval, String dtval, Long planHeadId, Pageable pageable) throws Exception {
        boolean firstMonth = dtval.contains(" ");
        boolean lastMonth = dtval.contains("后");
        dtval = dtval.replace("-", "").replace(" ","").replace("后","");
        PlanHeader planHeader = new PlanHeader();
        planHeader.setId(planHeadId);
        Map<String, List<IEPlanBusinessHeadSet>> headMapByHdnum;
        try {
            planHeader = this.getById(planHeader);
            headMapByHdnum = iePlanBusinessHeadSetService
                    .getAllByBukrsAndRptyp(ContextUtils.getCompany().getOrgcode(), "MR")
                    .stream()
                    .collect(Collectors.groupingBy(IEPlanBusinessHeadSet::getHdnum));
        } catch (Exception e) {
            log.error("无此计划，ID：{}，message：{}",e.getMessage());
            throw new Exception(e.getMessage());
        }

        //待处理的计划列表
        Map<String, List<RollPlanHeadDataArchive>> rollPlanMapByHtsno = rollPlanArchiveService
                .getHeadData(zbart, dmval, dtval, planHeadId, firstMonth, lastMonth)
                .stream()
                .collect(Collectors.groupingBy(RollPlanHeadDataArchive::getHtsno));
        List<Map<String, String>> htsnoList = new ArrayList<>(rollPlanMapByHtsno.size());
        Map<String, List<LocalDate>> weekMap = week(dtval);
        Assert.notNull(weekMap,"星期计算错误！"+dtval);
        //计算周期内合同的金额
        rollPlanMapByHtsno.forEach((htsno, rollPlanList)->{
            //TODO debug point
            if ("60101700021446".equals(htsno)){
                log.debug("debug point!");
            }
            Map<String, WearsType> htsnoMap = new HashMap<>();
            rollPlanList.forEach(rollPlan ->
                weekMap.forEach((week, dateList)->{
                    //如果滚动计划日期在本周内，则累计滚动计划金额
                    if(isInDuration(rollPlan.getDtval(), dateList, week, weekMap.size(), firstMonth, lastMonth)){
                        WearsType wears = new WearsType();
                        htsnoMap.put(week,wears);
                        IEPlanBusinessHeadSet headSet = headMapByHdnum.get(rollPlan.getHdnum()).get(0);
                        switch (headSet.getZtype()){
                            case "01":
                                wears.setBudget(wears.getBudget().add(rollPlan.getWears()));
                                break;
                            case "02":
                                wears.setProgress(wears.getProgress().add(rollPlan.getWears()));
                                break;
                            case "03":
                                wears.setSettlement(wears.getSettlement().add(rollPlan.getWears()));
                                break;
                            case "04":
                                wears.setWarranty(wears.getWarranty().add(rollPlan.getWears()));
                                break;
                        }
                    }
                })
            );
            //组装款项明细
            if(!ClassUtils.isEmpty(htsnoMap)){
                Map<String, String> map = new HashMap<>();
                htsnoList.add(map);
                map.put("htsno",htsno);
                htsnoMap.forEach((k,v)->{
                    StringBuilder sb = new StringBuilder();
                    BigDecimal amount = new BigDecimal("0.00");
                    if(v.getBudget().compareTo(BigDecimal.ZERO)>0){
                        sb.append("预：").append(v.getBudget().toString());
                        amount = amount.add(v.getBudget());
                    }
                    if(v.getSettlement().add(v.getProgress()).compareTo(BigDecimal.ZERO)>0){
                        if(!Strings.isNullOrEmpty(sb.toString())){
                            sb.append("$");
                        }
                        sb.append("结：").append(v.getSettlement().add(v.getProgress()).toString());
                        amount = amount.add(v.getSettlement()).add(v.getProgress());
                    }
                    if(v.getWarranty().compareTo(BigDecimal.ZERO)>0){
                        if(!Strings.isNullOrEmpty(sb.toString())){
                            sb.append("$");
                        }
                        sb.append("质：").append(v.getWarranty().toString());
                        amount = amount.add(v.getWarranty());
                    }
                    if(!Strings.isNullOrEmpty(sb.toString())){
                        map.put(k, sb.toString());
                        map.put(k+"_amount", amount.toString());
                    }
                });
            }
        });

        //补充htsno的取数指标数据iePlanSelectDataSetService
        String ckdate = planHeader.getCkdate();

        Set<String> htsnoSet = htsnoList.stream().map(i -> i.get("htsno")).collect(Collectors.toSet());
        //根据版本号，及htsno获取所有取数指标
        List<IEPlanSelectValueSet> selectValueSetList = iePlanSelectValueSetService.getContractByHtsnoSetAndVersion(htsnoSet, ckdate);
        //取数指标根据sdart分组
        Map<String, List<IEPlanSelectValueSet>> selectValueMapByHtsno = selectValueSetList
                .stream()
                .collect(Collectors.groupingBy(IEPlanSelectValueSet::getHtsno));

        htsnoList.forEach(htsno->{
            //根据合同流水号，获取取数指标及其值
            List<IEPlanSelectValueSet> sdartList = selectValueMapByHtsno.get(htsno.get("htsno"));
            sdartList.forEach(sdart-> htsno.put(sdart.getSdart(), sdart.getSdval()));
        });
        return htsnoList;
    }

    @Override
    public List<Map<String, Object>> getCavalByPlanHeadIdAndCaartAndDmart(Long planHeadId, String caart, String dmart, String zbart) {
        List<Map<String, Object>> list = new ArrayList<>();
        //维度列表
        Map<String, List<OrganizationSet>> orgMap = organizationSetService.getOrgMapByDmart(dmart);
        if(orgMap==null){
            return list;
        }
        Map<String, List<DmCalcStatistics>> statisticsMap = dmCalcStatisticsService
                .getStatisticsByDmartAndCaartAndVersion(dmart, caart, planHeaderDAO.findOne(planHeadId).getCkdate())
                .stream()
                .collect(Collectors.groupingBy(DmCalcStatistics::getDmval));
        //计划能力值相关项目编号列表
        Map<String, List<IEPlanBusinessItemSet>> imnumMap = iePlanBusinessItemSetService
                .getAllByCaart(caart)
                .stream()
                .collect(Collectors.groupingBy(IEPlanBusinessItemSet::getImnum));
        orgMap.forEach((k, v)->{
            Map<String, Object> map = new HashMap<>();
            //填充组织信息
            organizationSetService.fillMap(orgMap, map, dmart, k);
            //待统计抬头列表
            List<RollPlanHeadDataArchive> headList = rollPlanArchiveService.getHeadDataByPlanHeadIdAndDmvalAndZbart(zbart, dmart + ":" + k, planHeadId);
            if(ClassUtils.isEmpty(headList)){
               return;
            }
            //根据抬头ID列表，以及项目编号列表获取相关环节列表
            List<RollPlanItemDataArchive> itemList = rollPlanArchiveService.getItemDataByHeadIdAndImnum(
                    headList.stream().map(RollPlanHeadDataArchive::getRollId).collect(Collectors.toList()),
                    new ArrayList<>(imnumMap.keySet())
            );

            //获取待计算账期列表
            itemList = itemList.stream().filter(i->i.getCaval()!=null).collect(Collectors.toList());
            //取账期平均值
            OptionalDouble opt = itemList.stream().mapToInt(RollPlanItemDataArchive::getCaval).average();
            if(opt.isPresent()){
                map.put("caval",new BigDecimal(opt.getAsDouble()).setScale(0, BigDecimal.ROUND_HALF_DOWN).intValue());
                map.put("cavalNew",map.get("caval"));
            }else{
                return;
                // map.put("caval",0);
            }
            map.put("cavalHis", statisticsMap.get(k).get(0).getHisval());

            //合同数量
            List<Long> rollIdList = itemList.stream().map(RollPlanItemDataArchive::getHeadId).collect(Collectors.toList());
            headList = headList.stream().filter(i->rollIdList.contains(i.getRollId())).collect(Collectors.toList());
            long count = headList.stream().map(RollPlanHeadDataArchive::getHtsno).distinct().count();
            map.put("count", count);

            //合同总金额
            BigDecimal wears = headList.stream().map(RollPlanHeadDataArchive::getWears).reduce(BigDecimal.ZERO, BigDecimal::add);
            map.put("wears", wears.doubleValue());
            list.add(map);
        });
        return list;
    }

    @Override
    public int updateCavalByPlanHeadIdAndCaartAndDmartAndDmval(Long planHeadId, String caart, String dmart, String dmval, String zbart, Integer caval) {

        //待统计抬头列表
        List<RollPlanHeadDataArchive> headList = rollPlanArchiveService.getHeadDataByPlanHeadIdAndDmvalAndZbart(zbart, dmart + ":" + dmval, planHeadId);
        if(ClassUtils.isEmpty(headList)){
            return 0;
        }
        List<RollPlanItemDataArchive> changedList = new ArrayList<>();
        //计划能力值相关项目编号列表
        Map<String, List<IEPlanBusinessItemSet>> imnumMap = iePlanBusinessItemSetService
                .getAllByCaart(caart)
                .stream()
                .collect(Collectors.groupingBy(IEPlanBusinessItemSet::getImnum));

        //获取节点及后续节点列表
        Map<String, List<IEPlanBusinessItemSet>> itemMap = new HashMap<>(imnumMap.size());
        Set<String> itemSet = new HashSet<>();
        imnumMap.forEach((key,value)-> {
            List<IEPlanBusinessItemSet> nextItemList = iePlanBusinessItemSetService.getNextItemList(key);
            itemMap.put(key, nextItemList);
            itemSet.addAll(nextItemList.stream().map(IEPlanBusinessItemSet::getImnum).collect(Collectors.toList()));
        });

        //根据抬头ID列表，以及项目编号列表获取相关环节列表
        Map<Long, List<RollPlanItemDataArchive>> itemArchiveMap = rollPlanArchiveService.getItemDataByHeadIdAndImnum(
                headList.stream().map(RollPlanHeadDataArchive::getRollId).collect(Collectors.toList()),
                new ArrayList<>(itemSet)
        ).stream().collect(Collectors.groupingBy(RollPlanItemDataArchive::getHeadId));
        if(ClassUtils.isEmpty(itemArchiveMap)){
            return 0;
        }

        //更新当前节点能力值以及后续节点日期
        itemMap.forEach((imnum, item)->{
            if (Strings.isNullOrEmpty(imnum)) {
                return;
            }
            itemArchiveMap.forEach((headId, itemList)->{
                //根据项目编号分组
                Map<String, List<RollPlanItemDataArchive>> itemGroup = itemList.stream().collect(Collectors.groupingBy(RollPlanItemDataArchive::getImnum));
                //处理当前节点
                List<RollPlanItemDataArchive> imnumItemList = itemGroup.get(imnum);
                imnumItemList.forEach(i->{
                    if(IEPlanBusinessItemSet.GET.equals(i.getCtdtp())){
                        return;
                    }
                    //如果调减的天数大于能力值，则调减能力值天数
                    int days = caval + i.getCaval() > 0 ? caval:0-i.getCaval();
                    //更新历史能力值
                    i.setCaval(i.getCaval()+days);
                    //调整计划日期
                    adjustDtval(i, days);
                    changedList.add(i);
                    //更新后续节点日期
                    if(!ClassUtils.isEmpty(item)){
                        item.remove(0);//去掉当前已处理的节点
                        item.forEach(nextItem->{
                            List<RollPlanItemDataArchive> nextItemList = itemGroup.get(nextItem.getImnum());
                            if(!ClassUtils.isEmpty(nextItemList)){
                                nextItemList.forEach(l-> adjustDtval(l, days));
                                changedList.addAll(nextItemList);
                            }
                        });
                    }
                });
            });
        });
        if(!ClassUtils.isEmpty(changedList)){
            rollPlanArchiveService.saveItemList(changedList);
        }
        return changedList.size();
    }

    @Override
    public PlanHeader getLastMRPlan() {
        return planHeaderDAO.findTopByRptypOrderByCkdateDesc("MR");
    }

    private void adjustDtval(RollPlanItemDataArchive i, int days){
        String dtval = i.getDtval();
        if(!Strings.isNullOrEmpty(dtval)){
            try {
                i.setDtval(ClassUtils.StringToLocalDate(dtval).plusDays(days).toString().replaceAll("-",""));
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
    }

    private PlanHeaderVO coverToVO(PlanHeader header){
        PlanHeaderVO vo = new PlanHeaderVO();
        BeanUtils.copyProperties(header, vo);
        //公司代码描述
        vo.setButxt(organizationSetService.getByBukrs(vo.getBukrs()).getButxt());
        //审批状态描述
        vo.setSptxt(configDataSourceSetService.getDescByQcgrpAndQcode("STON", vo.getStonr()));
        //业务状态描述
        vo.setBstxt(configDataSourceSetService.getDescByQcgrpAndQcode("BSTA", vo.getBsta()));
        //报表类型描述
        vo.setRptxt(configDataSourceSetService.getDescByQcgrpAndQcode("RTYP", vo.getRptyp()));
        //货币名称
        vo.setKtext(configDataSourceSetService.getDescByQcgrpAndQcode("WAER", vo.getWaers()));
        //货币单位描述
        vo.setUnitx(configDataSourceSetService.getDescByQcgrpAndQcode("ZBUN", vo.getUnit()));
        return vo;
    }

    //获取yyyyMM月的每个星期及其起止日期
    private Map<String, List<LocalDate>> week(String yyyyMM){
        LocalDate firstDayOfMonth;
        try {
            firstDayOfMonth = ClassUtils.StringToLocalDate(yyyyMM + "01");
        } catch (ParseException e) {
            log.error(e.getMessage());
            return null;
        }
        Map<String, List<LocalDate>> weekMap = new HashMap<>();
        int days = firstDayOfMonth.lengthOfMonth();
        int week = 1;
        List<LocalDate> list = new ArrayList<>(2);
        while(true){
            //这个月的第一天
            if(firstDayOfMonth.getDayOfMonth() == 1){
                list.add(firstDayOfMonth);
                weekMap.put("week"+week,list);
            }
            //周一
            if(firstDayOfMonth.getDayOfWeek().equals(DayOfWeek.MONDAY)){
                list = new ArrayList<>(2);
                list.add(firstDayOfMonth);
                weekMap.put("week"+week,list);
            }
            //周日
            if(firstDayOfMonth.getDayOfWeek().equals(DayOfWeek.SUNDAY)){
                list.add(firstDayOfMonth);
                week++;
            }
            //这个月的最后一天
            if(firstDayOfMonth.getDayOfMonth() == days){
                list.add(firstDayOfMonth);
                break;
            }
            firstDayOfMonth = firstDayOfMonth.plusDays(1);
        }
        return weekMap;
    }

    //判断日期是否在这个期间
    private boolean isInDuration(String yyyyMMdd, List<LocalDate> dateList, String week, int weekSize, boolean firstMonth, boolean lastMonth){
        try {
            long today = ClassUtils.StringToLocalDate(yyyyMMdd).toEpochDay();
            long from = dateList.get(0).toEpochDay();
            long to = dateList.get(1).toEpochDay();
            //第一个月的第一周，需要包含以前所有的计划
            if("week1".equals(week) && firstMonth){
                return today <= to;
            }
            //最后一个月的最后一周，需要包含以后的所有计划
            if(("week"+weekSize).equals(week) && lastMonth){
                return from <= today;
            }
            return from <= today && today <= to;
        } catch (ParseException e) {
            return false;
        }
    }

    private void calcT800(List<PlanItem> itemList){
        // 计算T800（小计）
        itemList.stream().collect(Collectors.groupingBy(PlanItem::getDmval)).forEach((dmval,dmList)->
                dmList.stream().collect(Collectors.groupingBy(PlanItem::getZbart)).forEach((zbart, zbartList)->{
                    BigDecimal count = new BigDecimal(0);
                    PlanItem T800 = null;
                    for (PlanItem i : zbartList) {
                        count = count.add(ClassUtils.coverStringToBigDecimal(i.getZbval()));
                        if("T800".equals(i.getZtval())){
                            T800 = i;
                        }
                    }
                    if(T800 != null){
                        T800.setZbval(count.toString());
                    }
                })
        );
    }
}
