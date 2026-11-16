# 本应用的混淆规则。
#
# Room 和 Compose 都自带 consumer rules，不需要在这里重复声明。
# release 构建开启了 isMinifyEnabled + isShrinkResources，
# 如果以后引入靠反射找类/资源的库，在这里补 -keep 规则即可。
