package com.example.hybrid_ai_app.core.data.remote

import com.google.gson.annotations.SerializedName

data class GeneratePlanRequest(
    @SerializedName("planDuration") val planDuration: Int,
    @SerializedName("goal") val goal: String
)