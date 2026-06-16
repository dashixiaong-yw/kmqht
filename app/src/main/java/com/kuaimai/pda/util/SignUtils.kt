package com.kuaimai.pda.util

import java.security.MessageDigest

/**
 * 快麦API签名工具
 * MD5签名流程（与快麦开放平台一致）：
 * 1. 参数按key字母排序
 * 2. 拼接 key1value1key2value2...
 * 3. 前后追加appSecret
 * 4. MD5(拼接串) 转大写
 */
object SignUtils {

    /**
     * 计算快麦API签名
     * @param params 请求参数Map
     * @param appSecret 应用密钥
     * @return 签名字符串（大写hex）
     */
    fun sign(params: Map<String, String>, appSecret: String): String {
        // 1. 按key字母排序
        val sortedParams = params.toSortedMap()

        // 2. 拼接 key1value1key2value2...
        val concatenated = sortedParams.entries.joinToString("") { "${it.key}${it.value}" }

        // 3. 前后追加appSecret
        val signedString = appSecret + concatenated + appSecret

        // 4. MD5签名（与后端 _sign 函数一致）
        val md5 = MessageDigest.getInstance("MD5")
        val digest = md5.digest(signedString.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.uppercase()
    }

    /**
     * 验证签名
     * @param params 请求参数Map
     * @param appSecret 应用密钥
     * @param expectedSign 期望的签名
     * @return 签名是否匹配
     */
    fun verify(params: Map<String, String>, appSecret: String, expectedSign: String): Boolean {
        val calculated = sign(params, appSecret)
        return calculated.equals(expectedSign, ignoreCase = true)
    }
}
