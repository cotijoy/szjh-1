package com.magustek.szjh.plan.service.impl;

import com.magustek.szjh.basedataset.entity.IEPlanDimenValueSet;
import com.magustek.szjh.basedataset.entity.RollPlanHeadData;
import com.magustek.szjh.basedataset.entity.RollPlanItemData;
import com.magustek.szjh.basedataset.service.IEPlanDimenValueSetService;
import com.magustek.szjh.basedataset.service.IEPlanSelectValueSetService;
import com.magustek.szjh.basedataset.service.RollPlanDataService;
import com.magustek.szjh.plan.bean.PlanHeader;
import com.magustek.szjh.plan.bean.RollPlanHeadDataArchive;
import com.magustek.szjh.plan.bean.RollPlanItemDataArchive;
import com.magustek.szjh.plan.dao.RollPlanHeadDataArchiveDAO;
import com.magustek.szjh.plan.dao.RollPlanItemDataArchiveDAO;
import com.magustek.szjh.plan.service.RollPlanArchiveService;
import com.magustek.szjh.utils.ClassUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component("RollPlanArchiveService")
public class RollPlanArchiveServiceImpl implements RollPlanArchiveService {
    private RollPlanHeadDataArchiveDAO rollPlanHeadDataArchiveDAO;
    private RollPlanItemDataArchiveDAO rollPlanItemDataArchiveDAO;
    private RollPlanDataService rollPlanDataService;
    private IEPlanDimenValueSetService iePlanDimenValueSetService;
    private IEPlanSelectValueSetService iePlanSelectValueSetService;

    public RollPlanArchiveServiceImpl(RollPlanHeadDataArchiveDAO rollPlanHeadDataArchiveDAO, RollPlanItemDataArchiveDAO rollPlanItemDataArchiveDAO, RollPlanDataService rollPlanDataService, IEPlanDimenValueSetService iePlanDimenValueSetService, IEPlanSelectValueSetService iePlanSelectValueSetService) {
        this.rollPlanHeadDataArchiveDAO = rollPlanHeadDataArchiveDAO;
        this.rollPlanItemDataArchiveDAO = rollPlanItemDataArchiveDAO;
        this.rollPlanDataService = rollPlanDataService;
        this.iePlanDimenValueSetService = iePlanDimenValueSetService;
        this.iePlanSelectValueSetService = iePlanSelectValueSetService;
    }

    @Override
    public void copyData(PlanHeader planHeader) throws Exception {
        //合同基础数据表相应字段打上标记，不允许删除。
        int count = iePlanSelectValueSetService.updateReferencedByVersion("X",planHeader.getCkdate());
        log.warn("将版本为【{}】的合同基础数据打上引用标记，不允许删除！标记数据行数{}。", planHeader.getCkdate(), count);
        //每个合同的维度数据
        List<IEPlanDimenValueSet> dimenList = iePlanDimenValueSetService.getAllByVersion(planHeader.getCkdate());
        if(ClassUtils.isEmpty(dimenList)){
            throw new Exception("无维度数据！ckdate:"+planHeader.getCkdate());
        }
        Map<String, List<IEPlanDimenValueSet>> dmvalMapByHtsno = dimenList
                .stream()
                .collect(Collectors.groupingBy(IEPlanDimenValueSet::getHtsno));
        log.warn("取出维度数据，行数{}。", dimenList.size());

        //滚动计划抬头数据
        List<RollPlanHeadData> rollPlanHeadDataList = rollPlanDataService.getAllByVersion(planHeader.getCkdate(), planHeader.getBukrs());
        log.warn("取出滚动计划抬头数据，行数{}。", rollPlanHeadDataList.size());

        if(ClassUtils.isEmpty(rollPlanHeadDataList)){
            throw new Exception("无可复制的数据！ckdate:"+planHeader.getCkdate()+ ",bukrs:" +planHeader.getBukrs());
            //return;
        }

        List<RollPlanHeadDataArchive> rollPlanHeadDataArchiveList = new ArrayList<>();

        //滚动计划行项目数据
        List<RollPlanItemData> rollPlanItemDataList = rollPlanDataService.getAllByHead(rollPlanHeadDataList);

        log.warn("取出滚动计划行项目数据，行数{}。", rollPlanItemDataList.size());
        List<RollPlanItemDataArchive> rollPlanItemDataArchiveList = new ArrayList<>(rollPlanItemDataList.size());

        //复制抬头数据
        rollPlanHeadDataList.forEach(head->{
            RollPlanHeadDataArchive headArchive = new RollPlanHeadDataArchive();
            BeanUtils.copyProperties(head, headArchive);
            headArchive.setId(null);
            headArchive.setRollId(head.getId());
            headArchive.setPlanHeadId(planHeader.getId());

            //获取合同的维度数据，格式-D100:6010,D110:50003521,D120:SHIHAO1,
            if(!ClassUtils.isEmpty(dmvalMapByHtsno.get(head.getHtsno()))){
                Map<String, List<IEPlanDimenValueSet>> dmvalMapByDmart = dmvalMapByHtsno.get(head.getHtsno()).stream().collect(Collectors.groupingBy(IEPlanDimenValueSet::getDmart));
                StringBuilder builder = new StringBuilder();
                dmvalMapByDmart.forEach((k, v)->
                        builder.append(k).append(":").append(v.get(0).getDmval()).append(","));
                headArchive.setDmval(builder.toString());
            }

            rollPlanHeadDataArchiveList.add(headArchive);
        });
        log.warn("滚动计划抬头数据计算完毕，复制行数{}。", rollPlanHeadDataArchiveList.size());

        //复制行项目数据
        rollPlanItemDataList.forEach(item->{
            RollPlanItemDataArchive itemArchive = new RollPlanItemDataArchive();
            BeanUtils.copyProperties(item, itemArchive);
            itemArchive.setId(null);
            itemArchive.setHeadId(item.getHeadId().getId());
            itemArchive.setPlanHeadId(planHeader.getId());//计划抬头ID（使用父类的-headId与headArchive关联）
            rollPlanItemDataArchiveList.add(itemArchive);
        });
        log.warn("滚动计划行项目数据数据计算完毕，复制行数{}。", rollPlanItemDataArchiveList.size());
        rollPlanHeadDataArchiveDAO.save(rollPlanHeadDataArchiveList);
        log.warn("滚动计划抬头数据保存完毕，复制行数{}。", rollPlanHeadDataArchiveList.size());
        rollPlanItemDataArchiveDAO.save(rollPlanItemDataArchiveList);
        log.warn("滚动计划行项目数据数据保存完毕，复制行数{}。", rollPlanItemDataArchiveList.size());
    }

    @Transactional
    @Override
    public void deleteData(PlanHeader header) {
        //查找出待删除数据
        //List<RollPlanHeadDataArchive> headList = rollPlanHeadDataArchiveDAO.findAllByPlanHeadId(header.getId());
        //List<RollPlanItemDataArchive> itemList = rollPlanItemDataArchiveDAO.findAllByPlanHeadId(header.getId());
        //删除数据
        rollPlanHeadDataArchiveDAO.deleteAllByPlanHeadId(header.getId());
        rollPlanItemDataArchiveDAO.deleteAllByPlanHeadId(header.getId());
        //合同基础数据表相应字段删除引用标记，该数据将允许删除。
        int count = iePlanSelectValueSetService.updateReferencedByVersion(null,header.getCkdate());
        log.warn("将版本为【{}】的合同基础数据取消引用标记，将允许删除！标记数据行数{}。", header.getCkdate(), count);
    }

    @Override
    public List<RollPlanHeadDataArchive> getHeadDataArchiveList(PlanHeader header) {
        return rollPlanHeadDataArchiveDAO.findAllByPlanHeadId(header.getId());
    }

    @Override
    public List<RollPlanItemDataArchive> getItemDataArchiveList(PlanHeader header) {
        return rollPlanItemDataArchiveDAO.findAllByPlanHeadId(header.getId());
    }
}