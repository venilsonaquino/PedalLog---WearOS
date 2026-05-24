package com.pedallog.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Classe Application global do PedalLog.
 * 
 * Anotada com `@HiltAndroidApp` para disparar a geração de código do Hilt e 
 * iniciar a árvore de injeção de dependências no nível do Application.
 */
@HiltAndroidApp
class PedalLogApplication : Application()
