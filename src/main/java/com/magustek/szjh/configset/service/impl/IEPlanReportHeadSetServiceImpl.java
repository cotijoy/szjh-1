package com.magustek.szjh.configset.service.impl;

import com.google.common.collect.Lists;
import com.magustek.szjh.configset.bean.IEPlanReportHeadSet;
import com.magustek.szjh.configset.bean.IEPlanReportItemSet;
import com.magustek.szjh.configset.bean.OrganizationSet;
import com.magustek.szjh.configset.bean.vo.IEPlanReportHeadVO;
import com.magustek.szjh.configset.bean.vo.IEPlanReportItemVO;
import com.magustek.szjh.configset.dao.IEPlanReportHeadSetDAO;
import com.magustek.szjh.configset.service.IEPlanOperationSetService;
import com.magustek.szjh.configset.service.IEPlanReportHeadSetService;
import com.magustek.szjh.configset.service.IEPlanReportItemSetService;
import com.magustek.szjh.configset.service.OrganizationSetService;
import com.magustek.szjh.plan.utils.PlanConstant;
import com.magustek.szjh.user.bean.CompanyModel;
import com.magustek.szjh.utils.ClassUtils;
import com.magustek.szjh.utils.ContextUtils;
import com.magustek.szjh.utils.KeyValueBean;
import com.magustek.szjh.utils.OdataUtils;
import com.magustek.szjh.utils.http.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("IEPlanReportHeadSetService")
public class IEPlanReportHeadSetServiceImpl implements IEPlanReportHeadSetService {

    private final IEPlanReportHeadSetDAO iePlanReportHeadSetDAO;
    private final IEPlanReportItemSetService iePlanReportItemSetService;
    private final IEPlanOperationSetService iePlanOperationSetService;
    private final OrganizationSetService organizationSetService;
    private final HttpUtils httpUtils;

    public IEPlanReportHeadSetServiceImpl(IEPlanReportHeadSetDAO iePlanReportHeadSetDAO, IEPlanReportItemSetService iePlanReportItemSetService, IEPlanOperationSetService iePlanOperationSetService, OrganizationSetService organizationSetService, HttpUtils httpUtils) {
        this.iePlanReportHeadSetDAO = iePlanReportHeadSetDAO;
        this.iePlanReportItemSetService = iePlanReportItemSetService;
        this.iePlanOperationSetService = iePlanOperationSetService;
        this.organizationSetService = organizationSetService;
        this.httpUtils = httpUtils;
    }

    @Override
    public List<IEPlanReportHeadSet> save(List<IEPlanReportHeadSet> list) {
        list.removeIf(item-> !item.getMsgtype().equals("S"));
        if(list.size()>0) {
            iePlanReportHeadSetDAO.save(list);
        }else{
            log.error("IEPlanOperationSet 数据为空！");
        }
        return list;
    }

    @Override
    public List<IEPlanReportHeadSet> getAll() {
        return Lists.newArrayList(iePlanReportHeadSetDAO.findAll());
    }

    @Override
    public List<IEPlanReportHeadSet> getAllByRptyp(String rptyp) {
        return Lists.newArrayList(iePlanReportHeadSetDAO.findAllByRptyp(rptyp));
    }

    @Override
    public void deleteAll() {
        iePlanReportHeadSetDAO.deleteAll();
    }

    @Transactional
    @Override
    public List<IEPlanReportHeadSet> getAllFromDatasource() throws Exception {
        String result = httpUtils.getResultByUrl(OdataUtils.IEPlanReportHeadSet+"?", null, HttpMethod.GET);
        List<IEPlanReportHeadSet> list = OdataUtils.getListWithEntity(result, IEPlanReportHeadSet.class);
        iePlanReportHeadSetDAO.deleteAll();
        this.save(list);
        return list;
    }

    @Override
    public IEPlanReportHeadVO getReportConfigByBukrs(String bukrs, String rptyp, String orgdp, String rpdat) throws Exception {
        IEPlanReportHeadVO vo = getReportConfigByBukrs(bukrs, rptyp);
        //将传入的时间转化为LocalDate类型
        LocalDate startDate;
        if("Y".equals(vo.getPunit())){
            if("X".equals(vo.getTflag())){
                //绝对时间
                startDate = LocalDate.parse(rpdat+"-01-01");
            }else{
                //相对时间
                startDate = LocalDate.now();
            }
        }else if("M".equals(vo.getPunit())) {
            if("X".equals(vo.getTflag())){
                //绝对时间
                startDate = LocalDate.parse(rpdat+"-01-01");
            }else{
                //相对时间
                startDate = LocalDate.now();
            }
        }else{
            startDate = LocalDate.parse(rpdat);
        }
        //初始化x、y、z轴数据
        //用户如果编制部门报表，需要根据orgdp标记对组织进行筛选。
        axisType(vo,orgdp,startDate);
        return vo;
    }

    @Override
    public IEPlanReportHeadVO getReportConfigByBukrs(String bukrs, String rptyp) throws Exception {
        //获取报表配置抬头
        IEPlanReportHeadSet ar = iePlanReportHeadSetDAO.findByBukrsAndRptyp(bukrs, rptyp);
        IEPlanReportHeadVO vo = new IEPlanReportHeadVO();
        BeanUtils.copyProperties(ar, vo);
        //获取报表配置明细
        List<IEPlanReportItemSet> item = iePlanReportItemSetService.getByBukrsAndRptyp(bukrs, rptyp);
        List<IEPlanReportItemVO> itemVOList = new ArrayList<>();
        //获取指标描述
        Map<String, String> zbnamMap = iePlanOperationSetService.getZbnamMap();

        item.forEach(i->{
            IEPlanReportItemVO itemVO = new IEPlanReportItemVO();
            BeanUtils.copyProperties(i, itemVO);
            itemVO.setZbnam(zbnamMap.get(i.getZbart()));
            itemVOList.add(itemVO);
        });
        vo.setItemVOS(itemVOList);
        return vo;
    }

    @Override
    public List<CompanyModel> getBukrsList() {
        return null;
    }
    //初始化x、y、z轴数据
    private void axisType(IEPlanReportHeadVO vo, String orgdp, LocalDate startDate) throws Exception{
        ArrayList<String> axis = new ArrayList<>(3);
        ArrayList<ArrayList<KeyValueBean>> value = new ArrayList<>(3);
        axis.add(vo.getXaxis());
        axis.add(vo.getYaxis());
        axis.add(vo.getZaxis());

        for(String s : axis){
            switch (s){
                case PlanConstant.AXIS_ORG:
                    value.add(getORG(vo.getBukrs(), vo.getOrgdp(), orgdp));
                    break;
                case PlanConstant.AXIS_TIM:
                    value.add(getTIM(vo.getPunit(), vo.getPvalu(), startDate, true));
                    break;
                case PlanConstant.AXIS_ZB:
                    value.add(getZB(vo));
                    break;
                default :
                    log.error("axis error:" + s);
            }
        }

        vo.setXvalue(value.get(0));
        vo.setYvalue(value.get(1));
        vo.setZvalue(value.get(2));
    }
    //返回指定组织机构树
    private ArrayList<KeyValueBean> getORG(String voBukrs, String voOrgdp, String orgdp) throws Exception{
        ArrayList<KeyValueBean> keyValueBeans = new ArrayList<>();
        List<Object[]> list;
        KeyValueBean bean;
        CompanyModel company = ContextUtils.getCompany();
        switch (voOrgdp){
            case "C":
                OrganizationSet org = organizationSetService.getByBukrs(voBukrs);
                bean = new KeyValueBean();
                bean.put(org.getBukrs(), org.getButxt());
                keyValueBeans.add(bean);
                break;
            case "D":
                //如果是编制部门计划，就取当前用户所在部门
                if("D".equals(orgdp)){
                    bean = new KeyValueBean();
                    bean.put(company.getDeptcode(), company.getGtext());
                    keyValueBeans.add(bean);
                }else{
                    list = organizationSetService.getDpnumByBukrs(voBukrs);
                    for(Object[] o : list) {
                        bean = new KeyValueBean();
                        bean.put((String)o[0], (String)o[1]);
                        keyValueBeans.add(bean);
                    }
                }
                break;
            case "P":
                //如果是编制部门计划，取当前用户所在部门的岗位
                if("D".equals(orgdp)){
                    list = organizationSetService.getPonumByDpnum(company.getDeptcode());
                }else {
                    list = organizationSetService.getPonumByBukrs(voBukrs);
                }
                for(Object[] o : list) {
                    bean = new KeyValueBean();
                    bean.put((String)o[0], (String)o[1]);
                    keyValueBeans.add(bean);
                }
                break;
            case "U":
                //如果是编制部门计划，就取当前用户所在部门的用户
                if("D".equals(orgdp)){
                    list = organizationSetService.getUnameByDpnum(company.getDeptcode());
                }else {
                    list = organizationSetService.getUnameByBukrs(voBukrs);
                }
                for(Object[] o : list) {
                    bean = new KeyValueBean();
                    bean.put((String)o[0], (String)o[1]);
                    keyValueBeans.add(bean);
                }
                break;
            default :
                log.error("axis error:" + voOrgdp);
                throw new Exception("axis error:" + voOrgdp);
        }
        return keyValueBeans;
    }
    //返回指定日期列表
    private ArrayList<KeyValueBean> getTIM(String punit, String pvalue, LocalDate rpdat, boolean forward){
        int i = Integer.parseInt(pvalue);
        ArrayList<KeyValueBean> keyValueBeans = new ArrayList<>(i);

        for (;i>=0;i--){
            KeyValueBean item = new KeyValueBean();
            String date = ClassUtils.formatDate(rpdat, punit);
            item.put(date, date);
            keyValueBeans.add(item);
            rpdat = ClassUtils.getDate(rpdat, punit, 1, forward);
        }
        return keyValueBeans;
    }
    //获取所有经营指标分类
    private ArrayList<KeyValueBean> getZB(IEPlanReportHeadVO vo){
        List<IEPlanReportItemVO> list = vo.getItemVOS();
        ArrayList<KeyValueBean> keyValueBeans = new ArrayList<>(list.size());
        list.forEach(item->{
            KeyValueBean bean = new KeyValueBean();
            bean.put(item.getZbart(), item.getZbnam(), item.getOpera());
            keyValueBeans.add(bean);
        });
        return keyValueBeans;


    }
}
