package com.ctrip.framework.apollo.common.utils;

import com.ctrip.framework.apollo.core.utils.ByteUtil;
import com.ctrip.framework.apollo.core.utils.MachineUtil;
import com.google.common.base.Joiner;
import org.apache.commons.lang.time.FastDateFormat;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 唯一key生成
 */
public class UniqueKeyGenerator {

    /**
     * 安全的秒级时间戳格式化
     */
    private static final FastDateFormat TIMESTAMP_FORMAT = FastDateFormat.getInstance("yyyyMMddHHmmss");

    /**
     * 计数器，开始值是安全的
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(new SecureRandom().nextInt());

    /**
     * - 拼接器
     */
    private static final Joiner KEY_JOINER = Joiner.on("-");

    /**
     * 生成唯一key
     *
     * @param args 参数
     * @return 唯一key
     */
    public static String generate(Object... args) {
        String hexIdString = ByteUtil.toHexString(
                toByteArray(
                        // 参数的hash值
                        Objects.hash(args),
                        // 基于mac地址的机器唯一标识
                        MachineUtil.getMachineIdentifier(),
                        // 计数器
                        COUNTER.incrementAndGet()));

        return KEY_JOINER.join(
                TIMESTAMP_FORMAT.format(new Date()),
                hexIdString);
    }

    /**
     * 连接机器ID，计数器和key到字节数组
     * 仅检索ID和计数器的低3个字节以及keyHashCode的2个字节
     * <p>
     * Concat machine id, counter and key to byte array
     * Only retrieve lower 3 bytes of the id and counter and 2 bytes of the keyHashCode
     */
    protected static byte[] toByteArray(int keyHashCode, int machineIdentifier, int counter) {
        byte[] bytes = new byte[8];
        bytes[0] = ByteUtil.int1(keyHashCode);
        bytes[1] = ByteUtil.int0(keyHashCode);
        bytes[2] = ByteUtil.int2(machineIdentifier);
        bytes[3] = ByteUtil.int1(machineIdentifier);
        bytes[4] = ByteUtil.int0(machineIdentifier);
        bytes[5] = ByteUtil.int2(counter);
        bytes[6] = ByteUtil.int1(counter);
        bytes[7] = ByteUtil.int0(counter);
        return bytes;
    }


}
