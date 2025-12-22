package com.rafaeldiaz.orquestador_gold_rush_2025.core;

import com.rafaeldiaz.orquestador_gold_rush_2025.core.analysis.ProfitAccountant;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitAccountantTest {

    @Test
    void testProfitCalculation() throws IOException {
        // Limpieza previa (borrar archivo de prueba si existe)
        File file = new File("logs/balance_history.csv");
        if(file.exists()) file.delete();

        ProfitAccountant accountant = new ProfitAccountant();

        // DIA 1: Empezamos con $1000
        accountant.recordDailySnapshot(1000.0);

        // DIA 2: Ganamos $50 -> Total $1050
        accountant.recordDailySnapshot(1050.0);

        // Verificaciones
        assertTrue(file.exists(), "El archivo CSV debe existir");

        List<String> lines = Files.readAllLines(file.toPath());
        // Línea 0: Header
        // Línea 1: 1000.0, 0.0
        // Línea 2: 1050.0, 50.0

        System.out.println("--- CONTENIDO DEL CSV ---");
        lines.forEach(System.out::println);

        assertTrue(lines.size() >= 3);
        assertTrue(lines.get(2).contains("50.00"), "Debe registrar la ganancia de 50");
    }
}