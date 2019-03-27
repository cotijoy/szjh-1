package com.magustek.szjh.plan.dao;

import com.magustek.szjh.plan.bean.RollPlanItemDataArchive;
import com.magustek.szjh.plan.bean.vo.RollPlanItemDataArchiveVO;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.util.List;

/**
 * @author hexin
 */
public interface RollPlanItemDataArchiveDAO extends CrudRepository<RollPlanItemDataArchive, Long> {
    List<RollPlanItemDataArchive> findAllByPlanHeadId(Long planHeadId);
    List<RollPlanItemDataArchive> findAllByHeadId(Long rollPlanHeadId);
    @Modifying
    @Query("delete from RollPlanItemDataArchive where planHeadId=?1")
    void deleteAllByPlanHeadId(Long planHeadId);

    List<RollPlanItemDataArchive> findAllByHeadIdInAndImnumIn(List<Long> headIdList, List<String> imnumList);

    @Transactional
    @Modifying
    @Query(value = "insert into roll_plan_item_data_archive (id,crtime,crname,status,chdate,chname,caval,ctdtp,dtval,head_id,imnum,odue,plan_head_id,sdart,stval) " +
            "select id,crtime,crname,status,chdate,chname,caval,ctdtp,dtval,head_id,imnum,odue,?2,sdart,stval" +
            " from roll_plan_item_data where head_id in (select id from roll_plan_head_data where version=?1)", nativeQuery = true)
    int copyRollPlanItemByVersionAndPlanHeadId(String version, Long planHeadId);

    @Query(value = "select head.htsno, head.htnum, head.stval, head.wears, head.dmval, head.bukrs, head.hdnum, head.zbart, head.version," +
            "       item.caval, item.dtval, item.imnum, item.odue, item.sdart " +
            "from roll_plan_item_data_archive as item inner join roll_plan_head_data_archive as head on item.head_id=head.roll_id " +
            "where item.plan_head_id=?1  and item.ctdtp='C' and item.dtval between ?2 and ?3", nativeQuery = true)
    List<Object[]> findAllByPlanHeadIdAndCtdtpAndDtvalBetween(Long id, String start, String end);
}
