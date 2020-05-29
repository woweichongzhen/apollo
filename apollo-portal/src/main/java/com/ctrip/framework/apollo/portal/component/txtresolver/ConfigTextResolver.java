package com.ctrip.framework.apollo.portal.component.txtresolver;

import com.ctrip.framework.apollo.common.dto.ItemChangeSets;
import com.ctrip.framework.apollo.common.dto.ItemDTO;

import java.util.List;

/**
 * 配置文本解析器接口
 * 用户可以在文本模式下修改配置，因此需要解析文本
 * <p>
 * users can modify config in text mode.so need resolve text.
 */
public interface ConfigTextResolver {

    /**
     * 解析文本，创建 ItemChangeSets 对象
     *
     * @param namespaceId 命名空间id
     * @param configText  配置文本
     * @param baseItems   已存在的项
     * @return 改变的对象
     */
    ItemChangeSets resolve(long namespaceId, String configText, List<ItemDTO> baseItems);

}
