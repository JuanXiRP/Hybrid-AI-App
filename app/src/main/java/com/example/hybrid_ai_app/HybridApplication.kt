package com.example.hybrid_ai_app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Triggers Hilt's code generation, including a base class for your application
@HiltAndroidApp
class HybridApplication : Application()