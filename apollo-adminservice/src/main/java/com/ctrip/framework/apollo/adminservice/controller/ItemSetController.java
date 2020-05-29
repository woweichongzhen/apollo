package com.ctrip.framework.apollo.adminservice.controller;

import com.ctrip.framework.apollo.adminservice.aop.PreAcquireNamespaceLock;
import com.ctrip.framework.apollo.biz.service.ItemSetService;
import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * adminService 项保存API
 */
@RestController
public class ItemSetController {

    private final ItemSetService itemSetService;

    public ItemSetController(final ItemSetService itemSetService) {
        this.itemSetService = itemSetService;
    }

    /**
     * 批量更新命名空间的项
     *
     * @param appId         应用编号
     * @param clusterName   集群名称
     * @param namespaceName 命名空间名称
     * @param changeSet     改变集合
     * @return 返回体
     */
    @PreAcquireNamespaceLock
    @PostMapping("/apps/{appId}/clusters/{clusterName}/namespaces/{namespaceName}/itemset")
    public ResponseEntity<Void> create(@PathVariable String appId,
                                       @PathVariable String clusterName,
                                       @PathVariable String namespaceName,
                                       @RequestBody ItemChangeSets changeSet) {
        // 更新
        itemSetService.updateSet(appId, clusterName, namespaceName, changeSet);

        return ResponseEntity.status(HttpStatus.OK).build();
    }


}
