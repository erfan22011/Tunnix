package com.tunnix.parser

sealed class SubscriptionParserException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidDomainException(domain: String? = null) :
        SubscriptionParserException("کد اشتراک یا دامنه وارد شده مجاز نیست / Invalid subscription code or domain")

    class NetworkException(message: String = "خطا در برقراری ارتباط با سرور سابسکریپشن. لطفاً اتصال اینترنت خود را بررسی نمایید. / Unable to connect to server. Please check your internet connection.", cause: Throwable? = null) :
        SubscriptionParserException(message, cause)

    class EmptySubscriptionException :
        SubscriptionParserException("اشتراک خالی است یا سروری یافت نشد. / Subscription is empty or contains no valid servers.")

    class UnsupportedFormatException(message: String = "فرمت لینک یا محتوا نامعتبر است / Invalid format") :
        SubscriptionParserException(message)
}
