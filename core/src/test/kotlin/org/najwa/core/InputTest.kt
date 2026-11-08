package org.najwa.core

import org.junit.Assert.*
import org.junit.Test

class InputTest {
    @Test fun arabicAndPersianNumbers() {
        assertEquals(15, normalizedNumber(" ١٥ ").toInt())
        assertEquals(-21.543, normalizedNumber("−۲۱٫۵۴۳").toDouble(), 0.00001)
    }
    @Test fun invalidInputRemainsInvalid() {
        assertNull(normalizedNumber("١٢س").toIntOrNull())
        assertNull(normalizedNumber("١٫٢٫٣").toDoubleOrNull())
    }
    @Test fun searchIgnoresArabicMarksAndHamza() {
        assertEquals("أبها", searchCities("ابها").single().name)
        assertEquals("مكة المكرمة", searchCities("مَكَّـة").single().name)
        assertTrue(searchCities("nonexistent").isEmpty())
        assertEquals(Cities.all, searchCities(" "))
    }
}
