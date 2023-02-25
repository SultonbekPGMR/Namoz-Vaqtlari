package com.sultonbek1547.ramazontaqvim

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.view.View
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sultonbek1547.ramazontaqvim.databinding.ActivityMainBinding
import com.sultonbek1547.ramazontaqvim.model.NamozModel
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val dayType = object : TypeToken<NamozModel>() {}.type
    private var nextPrayerTime = "05:34"
    lateinit var requestQueue: RequestQueue
    private val urlDay = "https://islomapi.uz/api/present/day?region="
    private val urlWeek = "https://islomapi.uz/api/present/week?region="
    lateinit var currentItem: NamozModel
    private var nextTimeMillis = 0L
    lateinit var prayerTimesListWeek: List<NamozModel>
    private var currentWeekDayPosition = 0
    private lateinit var countDownTimer: CountDownTimer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(getDay)

        binding.apply {
            /**loading next Prayer day data*/
            btnRight.setOnClickListener {
                if (currentWeekDayPosition < prayerTimesListWeek.size - 1) {
                    binding.btnRight.isEnabled = false
                    binding.layoutBottom.slideLeft(300)
                }
            }


            /**loading previous day Prayer day data*/
            btnLeft.setOnClickListener {
                if (currentWeekDayPosition > 0) {
                    binding.btnLeft.isEnabled = false
                    binding.layoutBottom.slideRight(300)
                }
            }

        }

    }

    private val getDay =
        JsonObjectRequest(Request.Method.GET, urlDay + "Farg'ona", null, { response ->
            currentItem = Gson().fromJson(response.toString(), dayType)
            loadData()
            binding.layoutTop.slideDown(300)
            binding.layoutBottom.slideUp(300)
            binding.progressBar.visibility = View.INVISIBLE

            /** getting data for week */
            requestQueue.add(getWeek)


        }) {
            Toast.makeText(this, "no internet", Toast.LENGTH_SHORT).show()
            Handler().postDelayed({
                if (binding.progressBar.visibility == View.VISIBLE) {
                    Toast.makeText(this, "CHECK YOUR INTERNET and REENTER", Toast.LENGTH_SHORT)
                        .show()
                }
            }, 5000)
        }

    private val getWeek =
        JsonArrayRequest(Request.Method.GET, urlWeek + "Farg'ona", null, { response ->
            val type = object : TypeToken<List<NamozModel>>() {}.type
            prayerTimesListWeek = Gson().fromJson(response.toString(), type)
            getPositionOfCurrentItem()
        }, {})


    private fun loadData() {
        binding.apply {
            loadNextPrayerTime() //sometimes next sometimes current prayer time
            tvWeekday.text = currentItem.weekday
            tvTimeCurrent.text = nextPrayerTime
            tvRegion.text = currentItem.region
            tvBomdodTime.text = currentItem.times.tong_saharlik
            tvQuoyoshTime.text = currentItem.times.quyosh
            tvPeshinTime.text = currentItem.times.peshin
            tvAsrTime.text = currentItem.times.asr
            tvShomTime.text = currentItem.times.shom_iftor
            tvXuftonTime.text = currentItem.times.hufton
        }

    }

    private fun getPositionOfCurrentItem() {
        prayerTimesListWeek.forEach {
            if (it.weekday == currentItem.weekday)
                currentWeekDayPosition = prayerTimesListWeek.indexOf(it)
        }
    }

    private fun findClosestMatch(givenTime: String): String {
        val timesMap = mapOf(
            "Quyosh" to currentItem.times.quyosh,
            "Bomdod" to currentItem.times.tong_saharlik,
            "Peshin" to currentItem.times.peshin,
            "Asr" to currentItem.times.asr,
            "Shom" to currentItem.times.shom_iftor,
            "Xufton" to currentItem.times.hufton
        )

        var closestMatch: String? = null
        var closestDistance = Int.MAX_VALUE

        for ((key, value) in timesMap) {
            val distance = getTimeDistance(givenTime, value)
            if (distance < closestDistance) {
                closestMatch = key
                closestDistance = distance
            }
        }

        nextPrayerTime = timesMap[closestMatch]!!
        binding.tvTimeLeft.text = closestDistance.toString()
        return closestMatch ?: ""
    }


    private fun getTimeDistance(time1: String, time2: String): Int {
        val pattern = "HH:mm"
        val formatter = DateTimeFormatter.ofPattern(pattern)
        val dateTime1 = LocalTime.parse(time1, formatter)
        val dateTime2 = LocalTime.parse(time2, formatter)

        return abs(Duration.between(dateTime1, dateTime2).toMinutes().toInt())
    }

    private fun loadNextPrayerTime() {
        val targetTime = LocalTime.parse(
            "$nextPrayerTime:00", DateTimeFormatter.ofPattern("HH:mm:ss")
        )
        val currentTime = LocalTime.now()

        val duration =
            if (targetTime.isAfter(currentTime)) Duration.between(currentTime, targetTime)
            else Duration.between(currentTime, targetTime.plusHours(24))

        /**milisekunds for timer */
        nextTimeMillis = duration.toMillis()

        /** starting timer */
        startTimer()
        binding.apply {
            tvNameCurrent.text = findClosestMatch(
                LocalDateTime.now(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            )
            tvTimeCurrent.text = nextPrayerTime
        }
    }

    private fun startTimer() {
        val targetTime = LocalTime.parse("05:34:00", DateTimeFormatter.ofPattern("HH:mm:ss"))
        val currentTime = LocalTime.now()

        val duration =
            if (targetTime.isAfter(currentTime)) Duration.between(currentTime, targetTime)
            else Duration.between(currentTime, targetTime.plusHours(24))

        val nextTimeMillis = duration.toMillis()

        countDownTimer = object : CountDownTimer(nextTimeMillis, 1000) {
            override fun onTick(p0: Long) {
                val remainingTime = Duration.ofMillis(p0)
                val hours = String.format("%02d", remainingTime.toHours())
                val minutes = String.format("%02d", remainingTime.toMinutes() % 60)
                val seconds = String.format("%02d", remainingTime.toMillis() / 1000 % 60)

                binding.tvTimeLeft.text = "-$hours:$minutes:$seconds"
            }

            override fun onFinish() {
                loadNextPrayerTime()
            }
        }
        countDownTimer.start()
    }


    private fun View.slideUp(duration: Int) {
        visibility = View.VISIBLE
        val animate = TranslateAnimation(0f, 0f, 2 * (this.height).toFloat(), 0f)
        animate.duration = duration.toLong()
        animate.fillAfter = true
        this.startAnimation(animate)
    }

    private fun View.slideDown(duration: Int) {
        visibility = View.INVISIBLE
        val animate = TranslateAnimation(0f, 0f, -(this.height + 20).toFloat(), 0f)
        animate.duration = duration.toLong()
        animate.fillAfter = true
        this.startAnimation(animate)
    }

    private fun View.slideLeft(duration: Int) {
        var animate = TranslateAnimation(0f, -this.width.toFloat(), 0f, 0f)
        animate.duration = duration.toLong()
        animate.fillAfter = true
        this.startAnimation(animate)
        Handler().postDelayed({
            animate = TranslateAnimation(this.width.toFloat(), 0f, 0f, 0f)
            animate.duration = duration.toLong() - 50
            animate.fillAfter = true
            this.startAnimation(animate)
            currentItem = prayerTimesListWeek[++currentWeekDayPosition]
            countDownTimer.cancel()
            loadData()
            binding.btnRight.isEnabled = true
        }, 300)
    }


    private fun View.slideRight(duration: Int) {
        var animate = TranslateAnimation(0f, this.width.toFloat(), 0f, 0f)
        animate.duration = duration.toLong()
        animate.fillAfter = true
        this.startAnimation(animate)
        Handler().postDelayed({
            animate = TranslateAnimation(-this.width.toFloat(), 0f, 0f, 0f)
            animate.duration = duration.toLong() - 50
            animate.fillAfter = true
            this.startAnimation(animate)
            currentItem = prayerTimesListWeek[--currentWeekDayPosition]
            countDownTimer.cancel()
            loadData()
            binding.btnLeft.isEnabled = true
        }, 300)
    }


}