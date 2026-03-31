package com.hmdp.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 专门应对前端展示的订单视图对象 (View Object)
 * 完美的缝合了订单本身数据和其实体代金券信息
 */
@Data
public class VoucherOrderVO {
    // 订单ID，极其关键：从 Long 改为 String！
    // 否则雪花算法超长 ID 会被浏览器 JavaScript 精度截断导致订单号最后几位全变成 0
    private String id;

    // 基础订单数据
    private Long voucherId;
    private Integer status;
    private LocalDateTime createTime;

    // 从代金券表 (tb_voucher) 联查出的扩展展示数据
    private String title;
    private String subTitle;
    private Long payValue;
    private Long actualValue;
}
