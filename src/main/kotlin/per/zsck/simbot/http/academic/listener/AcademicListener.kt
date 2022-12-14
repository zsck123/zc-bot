package per.zsck.simbot.http.academic.listener

import cn.hutool.core.date.DateUnit
import cn.hutool.core.date.DateUtil
import love.forte.simboot.annotation.Filter
import love.forte.simboot.annotation.FilterValue
import love.forte.simbot.action.sendIfSupport
import love.forte.simbot.event.FriendMessageEvent
import love.forte.simbot.event.GroupMessageEvent
import love.forte.simbot.event.MessageEvent
import org.springframework.stereotype.Component
import per.zsck.simbot.common.annotation.RobotListen
import per.zsck.simbot.common.utils.MessageUtil.groupNumber
import per.zsck.simbot.core.permit.Permit
import per.zsck.simbot.core.state.GroupStateConstant
import per.zsck.simbot.core.state.GroupStateEnum
import per.zsck.simbot.core.state.service.GroupStateService
import per.zsck.simbot.http.academic.Academic
import per.zsck.simbot.http.academic.service.ClassMapService
import per.zsck.simbot.http.academic.service.ScheduleService
import per.zsck.simbot.http.academic.util.AcademicUtil
import java.sql.Date
import java.time.LocalDate

/**
 * @author zsck
 * @date   2022/11/10 - 8:30
 */

@Component
class AcademicListener(
    val scheduleService: ScheduleService,
    val classMapService: ClassMapService,
    val academicUtil: AcademicUtil,
    val groupStateService: GroupStateService,
    val academic: Academic
){

    @RobotListen(stateLeast = GroupStateEnum.OPENED_ALL)
    @Filter("/?{{index,\\d{1,2}}}")
    suspend fun MessageEvent.viewWeek(@FilterValue("index")index: Long ){

        scheduleService.getLessonsByWeek(index).apply {
            academicUtil.getCourseDetailMsg(this).forEach { sendIfSupport(it) }
        }
    }

    @RobotListen(stateLeast = GroupStateEnum.OPENED_ALL)
    @Filter("/?(w|W){{param,(\\+|-|=)?}}")
    suspend fun MessageEvent.week(@FilterValue("param")param: String){
        val firstDate = scheduleService.getFirstDate()
        val date = Date.valueOf(DateUtil.today())
        val standard = getBalanceByParam(param)

        val gap: Long = DateUtil.between(firstDate, date, DateUnit.WEEK, false) + standard.value

        if (gap >= 0) {
            sendIfSupport("${standard.week} ?????? $gap ???")
        } else {
            sendIfSupport("${standard.week} ????????????,?????????????????? ${DateUtil.between(
                date, firstDate, DateUnit.DAY, true)} ???")
            return
        }

        val scheduleList = scheduleService.getLessonsByWeek(gap)
        if (scheduleList.isEmpty()){
            sendIfSupport("${standard.week} ?????????")
            return
        }

        academicUtil.getCourseDetailMsg(scheduleList).forEach{
            sendIfSupport(it)
        }

    }

    @RobotListen(stateLeast = GroupStateEnum.OPENED_ALL)
    @Filter("/?(d|D){{param,(\\+|-|=)?}}")
    suspend fun MessageEvent.day(@FilterValue("param")param: String){
        val firstDate = scheduleService.getFirstDate()
        val standard = getBalanceByParam(param)
        val date = standard.getDateIfDay()

        if (date.before(firstDate)) {
            sendIfSupport("????????????????????????????????????:${DateUtil.between(date, firstDate, DateUnit.DAY, true)}???")
        } else {
            val gap = DateUtil.between(firstDate, date, DateUnit.DAY, true)
            val scheduleList = scheduleService.getLessonsByDate(date)
            sendIfSupport("${standard.day} ??????: $date ,????????? ${gap / 7 + 1} ???")
            if (scheduleList.isEmpty()) {
                sendIfSupport("???????????????")
                return
            }
            academicUtil.getCourseDetailMsg(scheduleList).forEach {
                sendIfSupport(it)
            }
        }
    }
    @RobotListen(stateLeast = GroupStateEnum.OPENED_ALL)
    @Filter("/?(f|F)\\s*{{name}}")
    suspend fun MessageEvent.find(@FilterValue("name")name: String){
        val classMapList = classMapService.likeClassName(name)

        if (classMapList.isEmpty()){
            sendIfSupport("?????????????????????????????????")
        }else{
            for (classMap in classMapList) {
                val classDetail = scheduleService.getClassDetail(classMap.id!!)

                sendIfSupport(academicUtil.getLessonInfoMsg(classMap, classDetail))
            }
        }
    }
    @RobotListen(permission = Permit.HOST)
    @Filter("/{{desState,(??????|??????)}}????????????")
    suspend fun GroupMessageEvent.setAcademicPush(@FilterValue("desState")desStateStr: String){
        val desState = if (desStateStr == "??????") { GroupStateConstant.LESSON_PUSH } else{ GroupStateConstant.UNABLE_LESSON_PUSH }

        val groupNumber = groupNumber()

        if (groupStateService.setGroupLessonPush(groupNumber, desState)){
            sendIfSupport("???${groupNumber}??????${desStateStr}????????????")
        }else{
            sendIfSupport("???${groupNumber}???????????????????????????${desStateStr}??????")
        }
    }

    @RobotListen(permission = Permit.HOST, stateLeast = GroupStateEnum.OPENED_ALL )
    @Filter("/?????????????")
    suspend fun MessageEvent.refreshSchedule() = sendIfSupport( academic.refresh() )


    fun getBalanceByParam(param: String): Standard {
        return when (param) {
            "+" -> {
                Standard.NEXT
            }
            "-" -> {
                Standard.LAST
            }
            else -> Standard.THIS
        }
    }

    enum class Standard(val value: Int, val week: String, val day: String) {
        //???????????????????????? ??? ????????????????????????
        THIS(1, "??????", "??????"), NEXT(2, "??????", "??????"), LAST(0, "??????", "??????");

        fun getDateIfDay(): Date{

            return Date.valueOf(LocalDate.now().let {
                if (value > 1) it.plusDays(1)
                else if (value < 1) it.minusDays(1)
                else it
            })
        }
    }
}