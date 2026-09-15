package io.github.willywonka644.fintracker.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Atm
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Liquor
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalPostOffice
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.PriceCheck
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Yard
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Registry of available Material icons that users can pick for categories.
 * Stored as name -> ImageVector so we can persist the name string in JSON/DB.
 *
 * The desktop module carries a byte-identical copy of this file: the registry
 * needs Compose, which the shared module does not depend on. Change one, change
 * the other — they drifted apart once already and the desktop lost CreditCard,
 * which silently downgraded the settlement category to the fallback glyph.
 *
 * Only ever ADD entries. [resolveIcon] falls back to MoreHoriz for unknown names,
 * so renaming or removing one silently blanks the icon of every category already
 * stored under that name. A few entries resolve to Icons.AutoMirrored because the
 * plain Icons.Filled variant is deprecated — the stored name is the same either way.
 */
val AVAILABLE_ICONS: Map<String, ImageVector> = linkedMapOf(
    // bisheriger Bestand — Reihenfolge unverändert
    "Payments"             to Icons.Filled.Payments,
    "Computer"             to Icons.Filled.Computer,
    "ShoppingCart"         to Icons.Filled.ShoppingCart,
    "AccountBalance"       to Icons.Filled.AccountBalance,
    "CreditCard"           to Icons.Filled.CreditCard,
    "LocalHospital"        to Icons.Filled.LocalHospital,
    "Home"                 to Icons.Filled.Home,
    "Flight"               to Icons.Filled.Flight,
    "Smartphone"           to Icons.Filled.Smartphone,
    "Shield"               to Icons.Filled.Shield,
    "Restaurant"           to Icons.Filled.Restaurant,
    "Fastfood"             to Icons.Filled.Fastfood,
    "DirectionsCar"        to Icons.Filled.DirectionsCar,
    "FitnessCenter"        to Icons.Filled.FitnessCenter,
    "School"               to Icons.Filled.School,
    "Pets"                 to Icons.Filled.Pets,
    "Work"                 to Icons.Filled.Work,
    "Build"                to Icons.Filled.Build,
    "Star"                 to Icons.Filled.Star,
    "MoreHoriz"            to Icons.Filled.MoreHoriz,
    // Geld & Finanzen
    "AccountBalanceWallet" to Icons.Filled.AccountBalanceWallet,
    "Savings"              to Icons.Filled.Savings,
    "TrendingUp"           to Icons.AutoMirrored.Filled.TrendingUp,
    "TrendingDown"         to Icons.AutoMirrored.Filled.TrendingDown,
    "Receipt"              to Icons.Filled.Receipt,
    "RequestQuote"         to Icons.Filled.RequestQuote,
    "Euro"                 to Icons.Filled.Euro,
    "Paid"                 to Icons.Filled.Paid,
    "PriceCheck"           to Icons.Filled.PriceCheck,
    "CurrencyExchange"     to Icons.Filled.CurrencyExchange,
    "Atm"                  to Icons.Filled.Atm,
    "Percent"              to Icons.Filled.Percent,
    // Wohnen & Haushalt
    "House"                to Icons.Filled.House,
    "Apartment"            to Icons.Filled.Apartment,
    "Chair"                to Icons.Filled.Chair,
    "Bed"                  to Icons.Filled.Bed,
    "Lightbulb"            to Icons.Filled.Lightbulb,
    "Bolt"                 to Icons.Filled.Bolt,
    "WaterDrop"            to Icons.Filled.WaterDrop,
    "LocalFireDepartment"  to Icons.Filled.LocalFireDepartment,
    "Plumbing"             to Icons.Filled.Plumbing,
    "Handyman"             to Icons.Filled.Handyman,
    "Construction"         to Icons.Filled.Construction,
    "Yard"                 to Icons.Filled.Yard,
    "Grass"                to Icons.Filled.Grass,
    "CleaningServices"     to Icons.Filled.CleaningServices,
    "Weekend"              to Icons.Filled.Weekend,
    // Essen & Trinken
    "ShoppingBasket"       to Icons.Filled.ShoppingBasket,
    "ShoppingBag"          to Icons.Filled.ShoppingBag,
    "LocalGroceryStore"    to Icons.Filled.LocalGroceryStore,
    "RestaurantMenu"       to Icons.Filled.RestaurantMenu,
    "LocalCafe"            to Icons.Filled.LocalCafe,
    "LocalBar"             to Icons.Filled.LocalBar,
    "LocalPizza"           to Icons.Filled.LocalPizza,
    "Cake"                 to Icons.Filled.Cake,
    "Icecream"             to Icons.Filled.Icecream,
    "LunchDining"          to Icons.Filled.LunchDining,
    "DinnerDining"         to Icons.Filled.DinnerDining,
    "BakeryDining"         to Icons.Filled.BakeryDining,
    "Liquor"               to Icons.Filled.Liquor,
    "EmojiFoodBeverage"    to Icons.Filled.EmojiFoodBeverage,
    // Mobilität
    "DirectionsBus"        to Icons.Filled.DirectionsBus,
    "DirectionsBike"       to Icons.AutoMirrored.Filled.DirectionsBike,
    "Train"                to Icons.Filled.Train,
    "Tram"                 to Icons.Filled.Tram,
    "Subway"               to Icons.Filled.Subway,
    "LocalGasStation"      to Icons.Filled.LocalGasStation,
    "LocalTaxi"            to Icons.Filled.LocalTaxi,
    "ElectricCar"          to Icons.Filled.ElectricCar,
    "TwoWheeler"           to Icons.Filled.TwoWheeler,
    "LocalParking"         to Icons.Filled.LocalParking,
    "EvStation"            to Icons.Filled.EvStation,
    "DirectionsWalk"       to Icons.AutoMirrored.Filled.DirectionsWalk,
    "Commute"              to Icons.Filled.Commute,
    // Gesundheit
    "MedicalServices"      to Icons.Filled.MedicalServices,
    "Medication"           to Icons.Filled.Medication,
    "Vaccines"             to Icons.Filled.Vaccines,
    "Healing"              to Icons.Filled.Healing,
    "MonitorHeart"         to Icons.Filled.MonitorHeart,
    "Psychology"           to Icons.Filled.Psychology,
    "SelfImprovement"      to Icons.Filled.SelfImprovement,
    "Spa"                  to Icons.Filled.Spa,
    // Freizeit & Kultur
    "SportsEsports"        to Icons.Filled.SportsEsports,
    "SportsSoccer"         to Icons.Filled.SportsSoccer,
    "SportsBasketball"     to Icons.Filled.SportsBasketball,
    "MusicNote"            to Icons.Filled.MusicNote,
    "Movie"                to Icons.Filled.Movie,
    "TheaterComedy"        to Icons.Filled.TheaterComedy,
    "Casino"               to Icons.Filled.Casino,
    "Palette"              to Icons.Filled.Palette,
    "Brush"                to Icons.Filled.Brush,
    "PhotoCamera"          to Icons.Filled.PhotoCamera,
    "Headphones"           to Icons.Filled.Headphones,
    "MenuBook"             to Icons.AutoMirrored.Filled.MenuBook,
    "LibraryBooks"         to Icons.AutoMirrored.Filled.LibraryBooks,
    "Nightlife"            to Icons.Filled.Nightlife,
    "Celebration"          to Icons.Filled.Celebration,
    "BeachAccess"          to Icons.Filled.BeachAccess,
    "Hiking"               to Icons.Filled.Hiking,
    "Pool"                 to Icons.Filled.Pool,
    "DownhillSkiing"       to Icons.Filled.DownhillSkiing,
    // Technik
    "Laptop"               to Icons.Filled.Laptop,
    "Tv"                   to Icons.Filled.Tv,
    "Headset"              to Icons.Filled.Headset,
    "Router"               to Icons.Filled.Router,
    "Memory"               to Icons.Filled.Memory,
    "Cloud"                to Icons.Filled.Cloud,
    "Print"                to Icons.Filled.Print,
    "Watch"                to Icons.Filled.Watch,
    "Keyboard"             to Icons.Filled.Keyboard,
    "Mouse"                to Icons.Filled.Mouse,
    "Storage"              to Icons.Filled.Storage,
    // Persönliches
    "Checkroom"            to Icons.Filled.Checkroom,
    "ContentCut"           to Icons.Filled.ContentCut,
    "Face"                 to Icons.Filled.Face,
    "Diamond"              to Icons.Filled.Diamond,
    "Redeem"               to Icons.Filled.Redeem,
    "CardGiftcard"         to Icons.Filled.CardGiftcard,
    "ChildCare"            to Icons.Filled.ChildCare,
    "BusinessCenter"       to Icons.Filled.BusinessCenter,
    "Badge"                to Icons.Filled.Badge,
    // Verträge & Verwaltung
    "Gavel"                to Icons.Filled.Gavel,
    "Description"          to Icons.Filled.Description,
    "Folder"               to Icons.Filled.Folder,
    "VerifiedUser"         to Icons.Filled.VerifiedUser,
    "Policy"               to Icons.Filled.Policy,
    "Assignment"           to Icons.AutoMirrored.Filled.Assignment,
    "LocalPostOffice"      to Icons.Filled.LocalPostOffice,
    "Mail"                 to Icons.Filled.Mail,
    "Phone"                to Icons.Filled.Phone,
    "Wifi"                 to Icons.Filled.Wifi,
    "SimCard"              to Icons.Filled.SimCard,
    // Reisen
    "Luggage"              to Icons.Filled.Luggage,
    "Hotel"                to Icons.Filled.Hotel,
    "Map"                  to Icons.Filled.Map,
    "Public"               to Icons.Filled.Public,
    "TravelExplore"        to Icons.Filled.TravelExplore,
    "Museum"               to Icons.Filled.Museum,
    // Allgemein
    "Favorite"             to Icons.Filled.Favorite,
    "Bookmark"             to Icons.Filled.Bookmark,
    "Category"             to Icons.Filled.Category,
    "Label"                to Icons.AutoMirrored.Filled.Label,
    "Info"                 to Icons.Filled.Info,
    "Help"                 to Icons.AutoMirrored.Filled.Help,
)

/** Resolve an icon name to its ImageVector, falling back to MoreHoriz. */
fun resolveIcon(name: String): ImageVector =
    AVAILABLE_ICONS[name] ?: Icons.Filled.MoreHoriz

/** All icon names in display order. */
val AVAILABLE_ICON_NAMES: List<String> = AVAILABLE_ICONS.keys.toList()
