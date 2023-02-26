package com.sultonbek1547.ramazontaqvim

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.TranslateAnimation
import android.widget.PopupMenu
import android.widget.TextView
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
import com.sultonbek1547.ramazontaqvim.model.Taqvim
import com.sultonbek1547.ramazontaqvim.utils.MyConstants.regions
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val dayType = object : TypeToken<Taqvim>() {}.type
    private var nextPrayerTime = "05:34"
    lateinit var requestQueue: RequestQueue
    private var currentRegionName = "Farg'ona"
    private var urlDay = "https://islomapi.uz/api/present/day?region=$currentRegionName"
    private var urlWeek = "https://islomapi.uz/api/present/week?region=$currentRegionName"
    private var urlMonth = "https://islomapi.uz/api/monthly?region=$currentRegionName&month="
    lateinit var currentItem: Taqvim
    private var nextTimeMillis = 0L
    private lateinit var prayerTimesListWeek: List<Taqvim>
    private lateinit var prayerTimesListMonth: List<Taqvim>
    private var currentWeekDayPosition = 0
    private lateinit var countDownTimer: CountDownTimer
    private var currentMonthPosition = Calendar.getInstance().get(Calendar.MONTH) + 1
    private val shortNamesOfMonth = arrayOf(
        "-yan",
        "-fev",
        "-mar",
        "-apr",
        "-may",
        "-iyun",
        "-iyul",
        "-avg",
        "-sen",
        "-okt",
        "-noy",
        "-dek"
    )
    private var monthSectionActivatedState = false

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


        /**dealing with toolbar menu*/
        binding.toolbar.setOnMenuItemClickListener {
            binding.toolbar.menu.getItem(0).isEnabled = false
            Handler().postDelayed({
                binding.toolbar.menu.getItem(0).isEnabled = true

            }, 500)
            if (monthSectionActivatedState) {
                monthSectionActivatedState = false
                Handler().postDelayed({
                    binding.toolbar.title = "Namoz vaqtlari"
                }, 200)
                binding.layoutMain.visibility = View.VISIBLE
                binding.layoutMonth.fadeOut(300)
                binding.toolbar.menu.getItem(0).setIcon(R.drawable.baseline_calendar_month_24)
                binding.layoutTop.slideDown(300)
                binding.layoutBottom.slideUp(300)
                return@setOnMenuItemClickListener true
            }
            Handler().postDelayed({
                binding.toolbar.title = "Taqvim"
                binding.layoutMain.visibility = View.INVISIBLE
            }, 200)
            binding.layoutMonth.fadeIn(300)
            monthSectionActivatedState = true
            binding.toolbar.menu.getItem(0).setIcon(R.drawable.baseline_close_24)
            binding.layoutBottom.slideDownClose(300)
            binding.layoutTop.slideUpClose(300)
            true
        }


        /**dealing with regions */
        val regionPopupMenu = PopupMenu(this, binding.tvRegion)
        for (i in regions.indices) {
            regionPopupMenu.menu.add(Menu.NONE, i, i, regions[i])
        }
        binding.tvRegion.setOnClickListener {
            regionPopupMenu.setOnMenuItemClickListener { menuItem ->
                val selectedRegionIndex = menuItem.itemId
                currentRegionName = regions[selectedRegionIndex]
                binding.tvRegion.text = currentRegionName
                urlDay = "https://islomapi.uz/api/present/day?region=$currentRegionName"
                urlWeek = "https://islomapi.uz/api/present/week?region=$currentRegionName"
                urlMonth = "https://islomapi.uz/api/monthly?region=$currentRegionName&month="
                /**  getting new data*/
                getAllThreeDataAtOnce()
                true
            }

            regionPopupMenu.show()


        }

    }

    private fun getAllThreeDataAtOnce() {
        /** first getting dayData */
        requestQueue.add(object : JsonObjectRequest(Request.Method.GET, urlDay, null,
            { response ->
                currentItem = Gson().fromJson(response.toString(), dayType)
                countDownTimer.cancel()
                loadData()

                /** Secondly getting weekData */
                requestQueue.add(JsonArrayRequest(Request.Method.GET, urlWeek, null,
                    { response ->
                        val type = object : TypeToken<List<Taqvim>>() {}.type
                        prayerTimesListWeek = Gson().fromJson(response.toString(), type)
                        getPositionOfCurrentItem()

                        /** finally getting data 0f Month */
                        requestQueue.add(
                            JsonArrayRequest(
                                Request.Method.GET,
                                urlMonth + currentMonthPosition,
                                null,
                                { response ->
                                    val type = object : TypeToken<List<Taqvim>>() {}.type
                                    prayerTimesListMonth =
                                        Gson().fromJson(response.toString(), type)
                                    /** dealing with table layout */
                                    loadToTable()
                                },
                                {})
                        )


                    },
                    { error ->
                        Toast.makeText(this, "Malumot topilmadi", Toast.LENGTH_SHORT).show()

                    }
                ))


            },
            {
                Toast.makeText(this, "Malumot topilmadi", Toast.LENGTH_SHORT).show()
            }
        ) {})

    }

    private val getDay = JsonObjectRequest(Request.Method.GET, urlDay, null, { response ->
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
        JsonArrayRequest(Request.Method.GET, urlWeek, null, { response ->
            val type = object : TypeToken<List<Taqvim>>() {}.type
            prayerTimesListWeek = Gson().fromJson(response.toString(), type)
            getPositionOfCurrentItem()

            /**getting data for Month*/
            requestQueue.add(getMonth)


        }, {})

    private val getMonth =
        JsonArrayRequest(Request.Method.GET, urlMonth + currentMonthPosition, null, { response ->
            val type = object : TypeToken<List<Taqvim>>() {}.type
            prayerTimesListMonth = Gson().fromJson(response.toString(), type)
            /** dealing with table layout */
            loadToTable()

        }, {})

    private fun loadToTable() {
        var tableRow = LayoutInflater.from(this).inflate(R.layout.rv_item, null)
        binding.myTableLayout.removeAllViews()
        binding.myTableLayout.addView(tableRow)
        Handler().postDelayed({
            binding.layoutBottom.visibility = View.INVISIBLE
            binding.layoutTop.visibility = View.INVISIBLE

        }, 2000)

        for (namozModel in prayerTimesListMonth) {
            tableRow = LayoutInflater.from(this).inflate(R.layout.rv_item, null)
            tableRow.findViewById<TextView>(R.id.day_name).text =
                (prayerTimesListMonth.indexOf(namozModel) + 1).toString() + shortNamesOfMonth[currentMonthPosition - 1]
            tableRow.findViewById<TextView>(R.id.asr).text = namozModel.times.asr
            tableRow.findViewById<TextView>(R.id.hufton).text = namozModel.times.hufton
            tableRow.findViewById<TextView>(R.id.peshin).text = namozModel.times.peshin
            tableRow.findViewById<TextView>(R.id.quyosh).text = namozModel.times.quyosh
            tableRow.findViewById<TextView>(R.id.shom_iftor).text = namozModel.times.shom_iftor
            tableRow.findViewById<TextView>(R.id.tong_saharlik).text =
                namozModel.times.tong_saharlik

            binding.myTableLayout.addView(tableRow)
        }


    }


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


    private fun findNextTime(givenTime: String) {
        val targetTime = LocalTime.parse(givenTime)
        val timeList = listOf(
            LocalTime.parse(currentItem.times.asr),
            LocalTime.parse(currentItem.times.hufton),
            LocalTime.parse(currentItem.times.peshin),
            LocalTime.parse(currentItem.times.quyosh),
            LocalTime.parse(currentItem.times.shom_iftor),
            LocalTime.parse(currentItem.times.tong_saharlik)
        )
        val sortedTimeList = timeList.sorted()
        val nameList = arrayOf(
            "Quyosh",
            "Bomdod",
            "Peshin",
            "Asr",
            "Shom",
            "Xufton"
        )
        for ((index, time) in sortedTimeList.withIndex()) {
            if (time.isAfter(targetTime)) {
                nextPrayerTime = time.format(DateTimeFormatter.ofPattern("HH:mm"))
                binding.tvNameCurrent.text = nameList[index]
                /** starting timer */
                startTimer()
                return
            }
        }
    }


    private fun loadNextPrayerTime() {
        binding.apply {
            findNextTime(
                LocalDateTime.now(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            )
            tvTimeCurrent.text = nextPrayerTime
        }
    }

    private fun startTimer() {
        val targetTime =
            LocalTime.parse("$nextPrayerTime:00", DateTimeFormatter.ofPattern("HH:mm:ss"))
        val currentTime = LocalTime.now().withSecond(0)

        val duration =
            if (targetTime.isAfter(currentTime)) Duration.between(currentTime, targetTime)
            else Duration.between(currentTime, targetTime.plusHours(24))

        /**milisekunds for timer */
        nextTimeMillis = duration.toMillis()
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

    private fun View.slideUpClose(duration: Int) {
        visibility = View.VISIBLE
        val animate = TranslateAnimation(0f, 0f, 0f, -(this.height + 300).toFloat())
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

    private fun View.slideDownClose(duration: Int) {
        visibility = View.INVISIBLE
        val animate = TranslateAnimation(0f, 0f, 0f, this.height.toFloat())
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
            loadDataOnlyBottom(prayerTimesListWeek[++currentWeekDayPosition])
            binding.btnRight.isEnabled = true
        }, 300)
    }

    private fun View.fadeIn(duration: Int) {
        visibility = View.INVISIBLE
        val animate = AlphaAnimation(0f, 1f)
        animate.duration = duration.toLong()
        animate.fillAfter = true
        this.startAnimation(animate)
        visibility = View.VISIBLE
    }

    private fun View.fadeOut(duration: Int) {
        val animate = AlphaAnimation(1f, 0f)
        animate.duration = duration.toLong()
        animate.fillAfter = true
        this.startAnimation(animate)
        this.visibility = View.GONE
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
            loadDataOnlyBottom(prayerTimesListWeek[--currentWeekDayPosition])
            binding.btnLeft.isEnabled = true
        }, 300)
    }

    private fun loadDataOnlyBottom(namozModel: Taqvim) {
        binding.apply {
            tvWeekday.text = namozModel.weekday
            tvTimeCurrent.text = nextPrayerTime
            tvRegion.text = namozModel.region
            tvBomdodTime.text = namozModel.times.tong_saharlik
            tvQuoyoshTime.text = namozModel.times.quyosh
            tvPeshinTime.text = namozModel.times.peshin
            tvAsrTime.text = namozModel.times.asr
            tvShomTime.text = namozModel.times.shom_iftor
            tvXuftonTime.text = namozModel.times.hufton
        }

    }
}