package per.zsck.simbot.http.mihoyo.sign.enums

import per.zsck.simbot.http.mihoyo.sign.SignConstant

/**
 * @author zsck
 * @date   2022/10/31 - 20:22
 */
enum class ServerType(val value: String) {
    /**
     * å®æ
     */
    OFFICIAL(SignConstant.REGION),

    /**
     * Bæ
     */
    FOREIGN(SignConstant.REGION2);
}