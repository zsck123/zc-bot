package per.zsck.simbot.core

import kotlinx.coroutines.runBlocking
import love.forte.simbot.event.Event
import love.forte.simbot.event.GroupMessageEvent
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import per.zsck.simbot.common.annotation.RobotListen
import per.zsck.simbot.common.logInfo
import per.zsck.simbot.core.permit.Permit
import per.zsck.simbot.core.permit.service.PermitDetailService
import per.zsck.simbot.core.state.GroupStateCache
import per.zsck.simbot.core.state.GroupStateEnum

/**
 * @author zsck
 * @date   2022/11/5 - 13:53
 */
@Aspect
@Component
class MessageAspect(
    val permitDetailService: PermitDetailService
    ) {

    @Around("@annotation(per.zsck.simbot.common.annotation.RobotListen) && @annotation(annotation))")
    fun ProceedingJoinPoint.doAroundAdvice(annotation: RobotListen): Any? {
        val event = args.find { it is Event } ?: return proceed()
        val start = System.currentTimeMillis()

        fun proceedSuccess(group: String = "好友消息"): Any? {
            logInfo("执行了监听器{}({})(群: {}), 拦截器耗时: {}"
            ,signature.name, annotation.desc, group, System.currentTimeMillis() - start)

            return proceed()
        }

        fun proceedFailed(tip: String, group: String) {
            logInfo("执行监听器{}({})(群: {}) 失败 : {}", signature.name, annotation.desc, group, tip)
        }

        if (event is GroupMessageEvent) {
            val group = runBlocking { event.group() }
            val author = runBlocking { event.author() }


            // 判断是否有权限
            if ( annotation.permission.value != Permit.MEMBER.value && annotation.permission.value >  permitDetailService.getPermit( author.id.toString() ).permit.value ) {
                if (annotation.noPermissionTip.isNotBlank()) {
                    runBlocking { group.send(annotation.noPermissionTip) }
                }
                return proceedFailed("权限不足", group.id.toString())

            }
            // 判断是否开机
            if (annotation.isBoot && GroupStateCache.STATE_MAP.getOrDefault(group.id.toString(), GroupStateEnum.CLOSED) == GroupStateEnum.CLOSED) {
                return proceedFailed("当前群未开机", group.id.toString())
            }

            return proceedSuccess(group.id.toString())
        }
        return proceedSuccess()
    }

}