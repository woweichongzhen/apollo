package com.ctrip.framework.apollo.biz.repository;

import com.ctrip.framework.apollo.biz.entity.ReleaseMessage;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 发布消息数据层
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ReleaseMessageRepository extends PagingAndSortingRepository<ReleaseMessage, Long> {

    /**
     * 获得大于 maxIdScanned 的 500 条 ReleaseMessage 记录，按照 id 升序
     *
     * @param id 消息id
     * @return 500条消息
     */
    List<ReleaseMessage> findFirst500ByIdGreaterThanOrderByIdAsc(Long id);

    /**
     * 查找最大的消息id
     *
     * @return 最大的消息id
     */
    ReleaseMessage findTopByOrderByIdDesc();

    /**
     * 获取最新的发布消息
     *
     * @param messages 消息
     * @return 发布消息
     */
    ReleaseMessage findTopByMessageInOrderByIdDesc(Collection<String> messages);

    /**
     * 获取头100条相同消息内容的消息，但id要小于消息id
     *
     * @param message 消息
     * @param id      消息id
     * @return 小于消息id的相同消息内容的消息
     */
    List<ReleaseMessage> findFirst100ByMessageAndIdLessThanOrderByIdAsc(String message, Long id);

    @Query("select message, max(id) as id from ReleaseMessage where message in :messages group by message")
    List<Object[]> findLatestReleaseMessagesGroupByMessages(@Param("messages") Collection<String> messages);
}
