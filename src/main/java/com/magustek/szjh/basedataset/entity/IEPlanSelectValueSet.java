package com.magustek.szjh.basedataset.entity;

import com.magustek.szjh.utils.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;

/**
 * 计划业务表：历史业务取数明细表
 * */
@Getter
@Setter
@Entity
public class IEPlanSelectValueSet extends BaseEntity {
    @Column(nullable = false, length = 14) private String htsno;//合同流水号
    @Column(nullable = false, length = 20) private String htnum;//合同管理编号
    @Column(length = 50) private String begda;//开始日期
    @Column(length = 50) private String endda;//结束日期
    @Column(nullable = false, length = 4) private String sdart;//业务取数指标
    @Column(nullable = false) private String sdval;//业务取数指标值
    @Column(length = 1) private String hflag;//历史计算标识
    @Column(length = 1) private String pflag;//计划编制标识
}
