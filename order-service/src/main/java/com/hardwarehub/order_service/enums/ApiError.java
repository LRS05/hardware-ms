package com.hardwarehub.order_service.enums;

public enum ApiError
{
    EMPTY_ORDER,
    ITEM_NOT_FOUND,
    ORDER_NOT_FOUND,
    PRODUCT_NOT_FOUND,
    PRODUCT_OUT_OF_STOCK,

    METHOD_ARGUMENT_TYPE_MISMATCH,
    HTTP_MESSAGE_NOT_READABLE
}
