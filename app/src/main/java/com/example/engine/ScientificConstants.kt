package com.example.engine

import java.math.BigDecimal

data class ScientificConstantInfo(
    val code: Int,
    val symbol: String,
    val name: String,
    val value: BigDecimal,
    val unit: String,
)

data class UnitConversionInfo(
    val code: Int,
    val label: String,
    val description: String,
    val convert: (BigDecimal) -> BigDecimal,
)

/**
 * 40 built-in Casio fx-991ES Plus CODATA scientific constants & 40 unit conversions.
 */
object ScientificConstants {

    val constants: List<ScientificConstantInfo> = listOf(
        ScientificConstantInfo(1, "mp", "Proton mass", BigDecimal("1.67262192369E-27"), "kg"),
        ScientificConstantInfo(2, "mn", "Neutron mass", BigDecimal("1.67492749804E-27"), "kg"),
        ScientificConstantInfo(3, "me", "Electron mass", BigDecimal("9.1093837015E-31"), "kg"),
        ScientificConstantInfo(4, "mμ", "Muon mass", BigDecimal("1.883531627E-28"), "kg"),
        ScientificConstantInfo(5, "a0", "Bohr radius", BigDecimal("5.29177210903E-11"), "m"),
        ScientificConstantInfo(6, "h", "Planck constant", BigDecimal("6.62607015E-34"), "J·s"),
        ScientificConstantInfo(7, "μN", "Nuclear magneton", BigDecimal("5.0507837461E-27"), "J/T"),
        ScientificConstantInfo(8, "μB", "Bohr magneton", BigDecimal("9.2740100783E-24"), "J/T"),
        ScientificConstantInfo(9, "ħ", "Reduced Planck constant", BigDecimal("1.054571817E-34"), "J·s"),
        ScientificConstantInfo(10, "α", "Fine-structure constant", BigDecimal("7.2973525693E-3"), ""),
        ScientificConstantInfo(11, "re", "Classical electron radius", BigDecimal("2.8179403262E-15"), "m"),
        ScientificConstantInfo(12, "λc", "Compton wavelength", BigDecimal("2.42631023867E-12"), "m"),
        ScientificConstantInfo(13, "γp", "Proton gyromagnetic ratio", BigDecimal("2.6752218744E8"), "s⁻¹T⁻¹"),
        ScientificConstantInfo(14, "λcp", "Proton Compton wavelength", BigDecimal("1.32140985539E-15"), "m"),
        ScientificConstantInfo(15, "λcn", "Neutron Compton wavelength", BigDecimal("1.31959090581E-15"), "m"),
        ScientificConstantInfo(16, "R∞", "Rydberg constant", BigDecimal("10973731.568160"), "m⁻¹"),
        ScientificConstantInfo(17, "u", "Atomic mass constant", BigDecimal("1.66053906660E-27"), "kg"),
        ScientificConstantInfo(18, "μp", "Proton magnetic moment", BigDecimal("1.41060679736E-26"), "J/T"),
        ScientificConstantInfo(19, "μe", "Electron magnetic moment", BigDecimal("-9.2847647043E-24"), "J/T"),
        ScientificConstantInfo(20, "μn", "Neutron magnetic moment", BigDecimal("-9.6623651E-27"), "J/T"),
        ScientificConstantInfo(21, "μμ", "Muon magnetic moment", BigDecimal("-4.49044830E-26"), "J/T"),
        ScientificConstantInfo(22, "F", "Faraday constant", BigDecimal("96485.33212"), "C/mol"),
        ScientificConstantInfo(23, "e₀", "Elementary charge", BigDecimal("1.602176634E-19"), "C"),
        ScientificConstantInfo(24, "NA", "Avogadro constant", BigDecimal("6.02214076E23"), "mol⁻¹"),
        ScientificConstantInfo(25, "k", "Boltzmann constant", BigDecimal("1.380649E-23"), "J/K"),
        ScientificConstantInfo(26, "Vm", "Molar volume (ideal gas)", BigDecimal("0.02241396954"), "m³/mol"),
        ScientificConstantInfo(27, "R", "Molar gas constant", BigDecimal("8.314462618"), "J/(mol·K)"),
        ScientificConstantInfo(28, "c0", "Speed of light in vacuum", BigDecimal("299792458"), "m/s"),
        ScientificConstantInfo(29, "c1", "First radiation constant", BigDecimal("3.741771852E-16"), "W·m²"),
        ScientificConstantInfo(30, "c2", "Second radiation constant", BigDecimal("0.01438776877"), "m·K"),
        ScientificConstantInfo(31, "σ", "Stefan-Boltzmann constant", BigDecimal("5.670374419E-8"), "W/(m²K⁴)"),
        ScientificConstantInfo(32, "ε0", "Electric constant", BigDecimal("8.8541878128E-12"), "F/m"),
        ScientificConstantInfo(33, "μ0", "Magnetic constant", BigDecimal("1.25663706212E-6"), "N/A²"),
        ScientificConstantInfo(34, "Φ0", "Magnetic flux quantum", BigDecimal("2.067833848E-15"), "Wb"),
        ScientificConstantInfo(35, "g", "Standard gravity", BigDecimal("9.80665"), "m/s²"),
        ScientificConstantInfo(36, "G0", "Conductance quantum", BigDecimal("7.748091729E-5"), "S"),
        ScientificConstantInfo(37, "Z0", "Impedance of vacuum", BigDecimal("376.730313668"), "Ω"),
        ScientificConstantInfo(38, "t", "Celsius temp (0 °C in K)", BigDecimal("273.15"), "K"),
        ScientificConstantInfo(39, "G", "Newtonian gravitation", BigDecimal("6.67430E-11"), "m³/(kg·s²)"),
        ScientificConstantInfo(40, "atm", "Standard atmosphere", BigDecimal("101325"), "Pa"),
    )

    fun byCode(code: Int): ScientificConstantInfo? = constants.firstOrNull { it.code == code }

    private fun factorConv(code: Int, label: String, desc: String, factor: String) =
        UnitConversionInfo(code, label, desc) { v -> BigMath.mul(v, BigDecimal(factor)) }

    private fun invFactorConv(code: Int, label: String, desc: String, factor: String) =
        UnitConversionInfo(code, label, desc) { v -> BigMath.div(v, BigDecimal(factor)) }

    val conversions: List<UnitConversionInfo> = listOf(
        factorConv(1, "in▸cm", "Inches to Centimeters", "2.54"),
        invFactorConv(2, "cm▸in", "Centimeters to Inches", "2.54"),
        factorConv(3, "ft▸m", "Feet to Meters", "0.3048"),
        invFactorConv(4, "m▸ft", "Meters to Feet", "0.3048"),
        factorConv(5, "yd▸m", "Yards to Meters", "0.9144"),
        invFactorConv(6, "m▸yd", "Meters to Yards", "0.9144"),
        factorConv(7, "mile▸km", "Miles to Kilometers", "1.609344"),
        invFactorConv(8, "km▸mile", "Kilometers to Miles", "1.609344"),
        factorConv(9, "n mile▸m", "Nautical miles to Meters", "1852"),
        invFactorConv(10, "m▸n mile", "Meters to Nautical miles", "1852"),
        factorConv(11, "acre▸m²", "Acres to Square meters", "4046.8564224"),
        invFactorConv(12, "m²▸acre", "Square meters to Acres", "4046.8564224"),
        factorConv(13, "gal(US)▸L", "US Gallons to Liters", "3.785411784"),
        invFactorConv(14, "L▸gal(US)", "Liters to US Gallons", "3.785411784"),
        factorConv(15, "gal(UK)▸L", "UK Gallons to Liters", "4.54609"),
        invFactorConv(16, "L▸gal(UK)", "Liters to UK Gallons", "4.54609"),
        factorConv(17, "pc▸km", "Parsecs to Kilometers", "3.0856775814913673E13"),
        invFactorConv(18, "km▸pc", "Kilometers to Parsecs", "3.0856775814913673E13"),
        factorConv(19, "km/h▸m/s", "km/h to m/s", "0.2777777777777777777777777777777778"),
        factorConv(20, "m/s▸km/h", "m/s to km/h", "3.6"),
        factorConv(21, "oz▸g", "Ounces to Grams", "28.349523125"),
        invFactorConv(22, "g▸oz", "Grams to Ounces", "28.349523125"),
        factorConv(23, "lb▸kg", "Pounds to Kilograms", "0.45359237"),
        invFactorConv(24, "kg▸lb", "Kilograms to Pounds", "0.45359237"),
        factorConv(25, "atm▸Pa", "Atmospheres to Pascals", "101325"),
        invFactorConv(26, "Pa▸atm", "Pascals to Atmospheres", "101325"),
        factorConv(27, "mmHg▸Pa", "mmHg to Pascals", "133.322387415"),
        invFactorConv(28, "Pa▸mmHg", "Pascals to mmHg", "133.322387415"),
        factorConv(29, "hp▸kW", "Horsepower to Kilowatts", "0.74569987158227022"),
        invFactorConv(30, "kW▸hp", "Kilowatts to Horsepower", "0.74569987158227022"),
        factorConv(31, "kgf/cm²▸Pa", "kgf/cm² to Pascals", "98066.5"),
        invFactorConv(32, "Pa▸kgf/cm²", "Pascals to kgf/cm²", "98066.5"),
        factorConv(33, "kgf·m▸J", "kgf·m to Joules", "9.80665"),
        invFactorConv(34, "J▸kgf·m", "Joules to kgf·m", "9.80665"),
        factorConv(35, "lbf/in²▸kPa", "psi to Kilopascals", "6.894757293168361"),
        invFactorConv(36, "kPa▸lbf/in²", "Kilopascals to psi", "6.894757293168361"),
        UnitConversionInfo(37, "°F▸°C", "Fahrenheit to Celsius") { f ->
            BigMath.div(BigMath.mul(BigMath.sub(f, BigDecimal("32")), BigDecimal("5")), BigDecimal("9"))
        },
        UnitConversionInfo(38, "°C▸°F", "Celsius to Fahrenheit") { c ->
            BigMath.add(BigMath.div(BigMath.mul(c, BigDecimal("9")), BigDecimal("5")), BigDecimal("32"))
        },
        factorConv(39, "J▸cal", "Joules to Calories", "0.2390057361376673"),
        factorConv(40, "cal▸J", "Calories to Joules", "4.184"),
    )
}
