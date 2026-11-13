package com.hitbha.photo.utils

import android.app.Activity
import android.util.Log
import java.lang.ref.WeakReference
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.hitbha.photo.BuildConfig

object AdManager {
    private val lock = Any()

    private var interstitialAd: InterstitialAd? = null
    private var adLoaded = false
    private var isLoading = false

    /** Queue of pending interstitial requests (activity + callback) to avoid lost callbacks. */
    private val pendingInterstitial = ArrayDeque<Pair<WeakReference<Activity>, () -> Unit>>()

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var rewardedAdLoaded = false
    private var rewardedIsLoading = false

    /** Queue of pending rewarded requests (activity + callback) to avoid lost callbacks. */
    private val pendingRewarded = ArrayDeque<Pair<WeakReference<Activity>, () -> Unit>>()

    private fun aliveActivity(ref: WeakReference<Activity>?): Activity? {
        val act = ref?.get() ?: return null
        if (act.isFinishing || act.isDestroyed) return null
        return act
    }

    private val INTERSTITIAL_ID = BuildConfig.AD_INTERSTITIAL_ID
    private val REWARDED_INTERSTITIAL_ID = BuildConfig.AD_REWARDED_INTERSTITIAL_ID

    private fun logLoadError(tag: String, adError: LoadAdError) {
        val ri = adError.responseInfo
        val mediation = ri?.mediationAdapterClassName ?: "n/a"
        val adapters = ri?.adapterResponses
            ?.joinToString(", ") { a ->
                val err = a.adError?.code
                "adapter=${a.adapterClassName} source=${a.adSourceName} " +
                    "latency=${a.latencyMillis}ms adErrorCode=${err}"
            }
            ?: "n/a"
        Log.w(
            tag,
            "$tag failed to load. code=${adError.code} domain=${adError.domain} " +
                "msg=${adError.message} cause=${adError.cause} " +
                "mediationAdapter=$mediation adapterResponses=[$adapters]"
        )
    }

    // ------------------------------------------------------------------------------------
    // Interstitial
    // ------------------------------------------------------------------------------------

    fun loadInterstitial(activity: Activity) {
        synchronized(lock) {
            if (adLoaded || isLoading) return
            isLoading = true
        }
        InterstitialAd.load(
            activity,
            INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    var next: Pair<WeakReference<Activity>, () -> Unit>? = null
                    synchronized(lock) {
                        interstitialAd = ad
                        adLoaded = true
                        isLoading = false
                        next = pendingInterstitial.removeFirstOrNull()
                    }
                    if (next != null) {
                        val act = aliveActivity(next.first)
                        if (act != null) {
                            showLoadedInterstitial(act, next.second)
                        } else {
                            next.second()
                        }
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    val callbacks = mutableListOf<() -> Unit>()
                    synchronized(lock) {
                        interstitialAd = null
                        adLoaded = false
                        isLoading = false
                        while (true) {
                            val n = pendingInterstitial.removeFirstOrNull() ?: break
                            callbacks.add(n.second)
                        }
                    }
                    logLoadError("InterstitialAd", adError)
                    callbacks.forEach { it() }
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        var showNow = false
        synchronized(lock) {
            if (adLoaded && interstitialAd != null && !isLoading) {
                showNow = true
            } else {
                pendingInterstitial.addLast(Pair(WeakReference(activity), onAdDismissed))
                if (!isLoading) {
                    loadInterstitial(activity)
                }
            }
        }
        if (showNow) {
            showLoadedInterstitial(activity, onAdDismissed)
        }
    }

    private fun showLoadedInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        val ad: InterstitialAd?
        synchronized(lock) {
            ad = if (adLoaded && interstitialAd != null) interstitialAd else null
            if (ad == null) {
                if (!isLoading) {
                    loadInterstitial(activity)
                }
                return
            }
        }
        val loadedAd = ad ?: return
        try {
            loadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.i("AdManager", "InterstitialAd showed full screen content")
                }

                override fun onAdClicked() {
                    Log.i("AdManager", "InterstitialAd was clicked")
                }

                override fun onAdDismissedFullScreenContent() {
                    synchronized(lock) {
                        adLoaded = false
                        interstitialAd = null
                    }
                    onAdDismissed()
                    loadInterstitial(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    synchronized(lock) {
                        adLoaded = false
                        interstitialAd = null
                    }
                    Log.w(
                        "AdManager",
                        "InterstitialAd failed to show. code=${adError.code} " +
                            "msg=${adError.message} domain=${adError.domain}"
                    )
                    onAdDismissed()
                    loadInterstitial(activity)
                }
            }
            loadedAd.show(activity)
        } catch (e: Exception) {
            synchronized(lock) {
                adLoaded = false
                interstitialAd = null
            }
            Log.e("AdManager", "InterstitialAd show threw exception", e)
            onAdDismissed()
            loadInterstitial(activity)
        }
    }

    // ------------------------------------------------------------------------------------
    // Rewarded interstitial
    // ------------------------------------------------------------------------------------

    fun loadRewardedInterstitial(activity: Activity) {
        synchronized(lock) {
            if (rewardedAdLoaded || rewardedIsLoading) return
            rewardedIsLoading = true
        }
        RewardedInterstitialAd.load(
            activity,
            REWARDED_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    var next: Pair<WeakReference<Activity>, () -> Unit>? = null
                    synchronized(lock) {
                        rewardedInterstitialAd = ad
                        rewardedAdLoaded = true
                        rewardedIsLoading = false
                        next = pendingRewarded.removeFirstOrNull()
                    }
                    if (next != null) {
                        val act = aliveActivity(next.first)
                        if (act != null) {
                            showLoadedRewardedInterstitial(act, next.second)
                        } else {
                            next.second()
                        }
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    val callbacks = mutableListOf<() -> Unit>()
                    synchronized(lock) {
                        rewardedInterstitialAd = null
                        rewardedAdLoaded = false
                        rewardedIsLoading = false
                        while (true) {
                            val n = pendingRewarded.removeFirstOrNull() ?: break
                            callbacks.add(n.second)
                        }
                    }
                    logLoadError("RewardedInterstitialAd", adError)
                    callbacks.forEach { it() }
                }
            }
        )
    }

    fun showRewardedInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        var showNow = false
        synchronized(lock) {
            if (rewardedAdLoaded && rewardedInterstitialAd != null && !rewardedIsLoading) {
                showNow = true
            } else {
                pendingRewarded.addLast(Pair(WeakReference(activity), onAdDismissed))
                if (!rewardedIsLoading) {
                    loadRewardedInterstitial(activity)
                }
            }
        }
        if (showNow) {
            showLoadedRewardedInterstitial(activity, onAdDismissed)
        }
    }

    private fun showLoadedRewardedInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        val ad: RewardedInterstitialAd?
        synchronized(lock) {
            ad = if (rewardedAdLoaded && rewardedInterstitialAd != null) rewardedInterstitialAd else null
            if (ad == null) {
                if (!rewardedIsLoading) {
                    loadRewardedInterstitial(activity)
                }
                return
            }
        }
        val loadedAd = ad ?: return
        try {
            loadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.i("AdManager", "RewardedInterstitialAd showed full screen content")
                }

                override fun onAdClicked() {
                    Log.i("AdManager", "RewardedInterstitialAd was clicked")
                }

                override fun onAdDismissedFullScreenContent() {
                    synchronized(lock) {
                        rewardedAdLoaded = false
                        rewardedInterstitialAd = null
                    }
                    onAdDismissed()
                    loadRewardedInterstitial(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    synchronized(lock) {
                        rewardedAdLoaded = false
                        rewardedInterstitialAd = null
                    }
                    Log.w(
                        "AdManager",
                        "RewardedInterstitialAd failed to show. code=${adError.code} " +
                            "msg=${adError.message} domain=${adError.domain}"
                    )
                    onAdDismissed()
                    loadRewardedInterstitial(activity)
                }
            }
            loadedAd.show(activity) { _: RewardItem -> }
        } catch (e: Exception) {
            synchronized(lock) {
                rewardedAdLoaded = false
                rewardedInterstitialAd = null
            }
            Log.e("AdManager", "RewardedInterstitialAd show threw exception", e)
            onAdDismissed()
            loadRewardedInterstitial(activity)
        }
    }
}
