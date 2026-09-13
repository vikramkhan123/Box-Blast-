package com.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// Aapke baki imports yahan aayenge...

class LevelSelectionActivity : AppCompatActivity() {

    // Aapka max unlocked level track karne ka variable (e.g. 14)
    private var currentUnlockedLevel: Int = 14 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_level_selection) // Yahan aapka layout naam aayega

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewLevels) // Apni ID check karein
        
        // Layout Manager setup
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        // Yahan aap apna adapter set karenge
        // val adapter = LevelAdapter(...)
        // recyclerView.adapter = adapter

        // --- AUTO SCROLL LOGIC ---
        // Jaise hi activity khule, seedha current level par scroll karein
        val targetPosition = if (currentUnlockedLevel > 0) currentUnlockedLevel - 1 else 0
        
        // scrollToPositionWithOffset seedha us item ko screen pe le aayega bina animation ke
        layoutManager.scrollToPositionWithOffset(targetPosition, 100) 
    }
}
