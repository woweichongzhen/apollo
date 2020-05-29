package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.GrayReleaseRule;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * 灰度发布规则数据层
 */
public interface GrayReleaseRuleRepository extends PagingAndSortingRepository<GrayReleaseRule, Long> {

    /**
     * 获取分支最后的灰度发布规则
     */
    GrayReleaseRule findTopByAppIdAndClusterNameAndNamespaceNameAndBranchNameOrderByIdDesc(String appId,
                                                                                           String clusterName,
                                                                                           String namespaceName,
                                                                                           String branchName);

    /**
     * 获取灰度规则集合
     */
    List<GrayReleaseRule> findByAppIdAndClusterNameAndNamespaceName(String appId,
                                                                    String clusterName, String namespaceName);

    /**
     * 大于id的灰度规则，扫描500条
     */
    List<GrayReleaseRule> findFirst500ByIdGreaterThanOrderByIdAsc(Long id);

}
