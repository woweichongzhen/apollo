package com.ctrip.framework.apollo.portal.service;

import com.ctrip.framework.apollo.common.constants.GsonType;
import com.ctrip.framework.apollo.common.dto.PageDTO;
import com.ctrip.framework.apollo.common.dto.ReleaseDTO;
import com.ctrip.framework.apollo.common.dto.ReleaseHistoryDTO;
import com.ctrip.framework.apollo.common.entity.EntityPair;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.entity.bo.ReleaseHistoryBO;
import com.ctrip.framework.apollo.portal.environment.Env;
import com.ctrip.framework.apollo.portal.util.RelativeDateFormat;
import com.google.gson.Gson;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReleaseHistoryService {

    private Gson gson = new Gson();


    private final AdminServiceAPI.ReleaseHistoryAPI releaseHistoryAPI;
    private final ReleaseService releaseService;

    public ReleaseHistoryService(final AdminServiceAPI.ReleaseHistoryAPI releaseHistoryAPI,
                                 final ReleaseService releaseService) {
        this.releaseHistoryAPI = releaseHistoryAPI;
        this.releaseService = releaseService;
    }

    /**
     * 查找最后一次发布历史
     *
     * @param env       环境
     * @param releaseId 发布id
     * @param operation 操作者
     * @return 发布历史bo
     */
    public ReleaseHistoryBO findLatestByReleaseIdAndOperation(Env env, long releaseId, int operation) {
        // 分页查找最后一次发布历史
        PageDTO<ReleaseHistoryDTO> pageDTO = releaseHistoryAPI.findByReleaseIdAndOperation(
                env,
                releaseId,
                operation,
                0,
                1);
        if (pageDTO != null && pageDTO.hasContent()) {
            ReleaseHistoryDTO releaseHistory = pageDTO.getContent().get(0);
            ReleaseDTO release = releaseService.findReleaseById(env, releaseHistory.getReleaseId());
            return transformReleaseHistoryDTO2BO(releaseHistory, release);
        }

        return null;
    }

    /**
     * 查找上一次发布历史
     *
     * @param env               环境
     * @param previousReleaseId 上一次的发布id
     * @param operation         操作者
     * @return 最后一次发布历史
     */
    public ReleaseHistoryBO findLatestByPreviousReleaseIdAndOperation(Env env, long previousReleaseId, int operation) {
        // 仅查询一条发布历史
        PageDTO<ReleaseHistoryDTO> pageDTO = releaseHistoryAPI.findByPreviousReleaseIdAndOperation(
                env,
                previousReleaseId,
                operation,
                0,
                1);
        if (pageDTO != null && pageDTO.hasContent()) {
            ReleaseHistoryDTO releaseHistory = pageDTO.getContent().get(0);
            // 查找发布历史对应的发布信息
            ReleaseDTO release = releaseService.findReleaseById(env, releaseHistory.getReleaseId());
            // 转换dto为bo
            return transformReleaseHistoryDTO2BO(releaseHistory, release);
        }

        return null;
    }

    public List<ReleaseHistoryBO> findNamespaceReleaseHistory(String appId, Env env, String clusterName,
                                                              String namespaceName, int page, int size) {
        PageDTO<ReleaseHistoryDTO> result = releaseHistoryAPI.findReleaseHistoriesByNamespace(appId, env, clusterName,
                namespaceName, page, size);
        if (result == null || !result.hasContent()) {
            return Collections.emptyList();
        }

        List<ReleaseHistoryDTO> content = result.getContent();
        Set<Long> releaseIds = new HashSet<>();
        for (ReleaseHistoryDTO releaseHistoryDTO : content) {
            long releaseId = releaseHistoryDTO.getReleaseId();
            if (releaseId != 0) {
                releaseIds.add(releaseId);
            }
        }

        List<ReleaseDTO> releases = releaseService.findReleaseByIds(env, releaseIds);

        return transformReleaseHistoryDTO2BO(content, releases);
    }

    private List<ReleaseHistoryBO> transformReleaseHistoryDTO2BO(List<ReleaseHistoryDTO> source,
                                                                 List<ReleaseDTO> releases) {

        Map<Long, ReleaseDTO> releasesMap = BeanUtils.mapByKey("id", releases);

        List<ReleaseHistoryBO> bos = new ArrayList<>(source.size());
        for (ReleaseHistoryDTO dto : source) {
            ReleaseDTO release = releasesMap.get(dto.getReleaseId());
            bos.add(transformReleaseHistoryDTO2BO(dto, release));
        }

        return bos;
    }

    /**
     * 转换发布历史dto和发布dto为发布历史bo
     *
     * @param dto     发布历史dto
     * @param release 发布dto
     * @return 发布历史bo
     */
    private ReleaseHistoryBO transformReleaseHistoryDTO2BO(ReleaseHistoryDTO dto, ReleaseDTO release) {
        ReleaseHistoryBO bo = new ReleaseHistoryBO();
        bo.setId(dto.getId());
        bo.setAppId(dto.getAppId());
        bo.setClusterName(dto.getClusterName());
        bo.setNamespaceName(dto.getNamespaceName());
        bo.setBranchName(dto.getBranchName());
        bo.setReleaseId(dto.getReleaseId());
        bo.setPreviousReleaseId(dto.getPreviousReleaseId());
        bo.setOperator(dto.getDataChangeCreatedBy());
        bo.setOperation(dto.getOperation());
        Date releaseTime = dto.getDataChangeLastModifiedTime();
        bo.setReleaseTime(releaseTime);
        bo.setReleaseTimeFormatted(RelativeDateFormat.format(releaseTime));
        bo.setOperationContext(dto.getOperationContext());
        //set release info
        setReleaseInfoToReleaseHistoryBO(bo, release);

        return bo;
    }

    /**
     * 设置发布信息到bo中
     *
     * @param bo      发布历史bo
     * @param release 发布信息
     */
    private void setReleaseInfoToReleaseHistoryBO(ReleaseHistoryBO bo, ReleaseDTO release) {
        if (release != null) {
            bo.setReleaseTitle(release.getName());
            bo.setReleaseComment(release.getComment());

            Map<String, String> configuration = gson.fromJson(release.getConfigurations(), GsonType.CONFIG);
            List<EntityPair<String>> items = new ArrayList<>(configuration.size());
            for (Map.Entry<String, String> entry : configuration.entrySet()) {
                // 族汉换发布配置为实体键值对
                EntityPair<String> entityPair = new EntityPair<>(entry.getKey(), entry.getValue());
                items.add(entityPair);
            }
            bo.setConfiguration(items);
        } else {
            // 无发布信息
            bo.setReleaseTitle("no release information");
            bo.setConfiguration(null);
        }
    }
}
