package com.wavebeat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SongListActivity : AppCompatActivity() {

    private lateinit var titles: Array<String>
    private lateinit var artists: Array<String>
    private var currentIndex = 0

    companion object {
        const val EXTRA_TITLES = "titles"
        const val EXTRA_ARTISTS = "artists"
        const val EXTRA_CURRENT = "currentIndex"
        const val EXTRA_INDEX = "selectedIndex"
        const val REQUEST_PICK = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_list)

        titles = intent.getStringArrayExtra(EXTRA_TITLES) ?: emptyArray()
        artists = intent.getStringArrayExtra(EXTRA_ARTISTS) ?: emptyArray()
        currentIndex = intent.getIntExtra(EXTRA_CURRENT, 0)

        findViewById<TextView>(R.id.songCount).text = "${titles.size} tracks"
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }

        val list = findViewById<ListView>(R.id.songList)
        list.adapter = SongAdapter()
        list.setOnItemClickListener { _, _, position, _ ->
            val result = Intent()
            result.putExtra(EXTRA_INDEX, position)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    private inner class SongAdapter : BaseAdapter() {
        override fun getCount(): Int = titles.size
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            val view = convertView ?: layoutInflater.inflate(
                R.layout.item_song, parent, false
            )
            val index = view.findViewById<TextView>(R.id.songIndex)
            val title = view.findViewById<TextView>(R.id.songRowTitle)
            val artist = view.findViewById<TextView>(R.id.songRowArtist)

            index.text = (position + 1).toString().padStart(2, '0')
            title.text = titles[position]
            artist.text = artists[position].takeIf { !it.isNullOrBlank() && it != "<unknown>" } ?: "—"
            title.isSelected = (position == currentIndex)
            if (position == currentIndex) {
                title.setTextColor(0xFFE94560.toInt())
                index.setTextColor(0xFFE94560.toInt())
            } else {
                title.setTextColor(0xFFFFFFFF.toInt())
                index.setTextColor(0xFF555555.toInt())
            }
            return view
        }
    }
}