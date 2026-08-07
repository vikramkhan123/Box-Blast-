package com.example

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false

    // Aapki ORIGINAL Rewarded Ad Unit ID
    private const val AD_UNIT_ID = "ca-app-pub-4346513942475662/6917543938" 

    fun loadRewardAd(activity: Activity) {
        if (rewardedAd != null || isAdLoading) return
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(activity, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("AdManager", "Ad failed to load: ${adError.message}")
                rewardedAd = null
                isAdLoading = false
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d("AdManager", "Ad was loaded.")
                rewardedAd = ad
                isAdLoading = false
            }
        })
    }

    fun showRewardAd(activity: Activity, onAdClosed: (Boolean) -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardAd(activity) // Agle use ke liye naya ad load karein
                }
                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    rewardedAd = null
                    onAdClosed(false)
                }
            }

            rewardedAd?.show(activity) { rewardItem ->
                // User ne poora ad dekh liya hai
                onAdClosed(true)
            }
        } else {
            // Agar ad load nahi hua hai ya internet issue hai
            Log.d("AdManager", "The rewarded ad wasn't ready yet.")
            onAdClosed(false)
            loadRewardAd(activity)
        }
    }
}
