package com.example

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private var rewardedAd: RewardedAd? = null
    // Test Reward Ad ID
    private const val REWARD_AD_ID = "ca-app-pub-3940256099942544/5224354917"

    fun loadRewardAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARD_AD_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) { rewardedAd = null }
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
        })
    }

    fun showRewardAd(activity: Activity, onRewardHandled: (Boolean) -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardAd(activity) // Load next ad
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    onRewardHandled(false)
                }
            }
            rewardedAd?.show(activity) { rewardItem ->
                onRewardHandled(true) // User ne ad dekha, reward de do!
            }
        } else {
            // Agar ad load nahi hua (No internet), toh free reward de do
            onRewardHandled(true)
            loadRewardAd(activity)
        }
    }
}
