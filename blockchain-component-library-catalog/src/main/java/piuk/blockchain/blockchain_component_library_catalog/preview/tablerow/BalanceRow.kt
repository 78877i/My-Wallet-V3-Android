package piuk.blockchain.blockchain_component_library_catalog.preview.tablerow

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.blockchain.componentlib.tablerow.BalanceStackedIconTableRow
import com.blockchain.componentlib.tablerow.BalanceTableRow
import com.blockchain.componentlib.tag.TagType
import com.blockchain.componentlib.tag.TagViewState
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme

@Preview(name = "Default", group = "Balance Row")
@Composable
fun BalanceTableRowPreview() {
    AppTheme {
        AppSurface {
            BalanceTableRow(
                titleStart = buildAnnotatedString {
                    append("Bitcoin")
                },
                titleEnd = buildAnnotatedString {
                    append("\$44,403.13")
                },
                bodyStart = buildAnnotatedString {
                    append("BTC")
                },
                bodyEnd = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = AppTheme.colors.error)) {
                        append("↓ 12.32%")
                    }
                },
                startIconUrl = "https://www.blockchain.com/static/img/prices/prices-btc.svg",
                tags = listOf(),
                onClick = {},
            )
        }
    }
}

@Preview(name = "Tag", group = "Balance Row")
@Composable
fun BalanceTagTableRowPreview() {
    AppTheme {
        AppSurface {
            BalanceTableRow(
                titleStart = buildAnnotatedString {
                    append("Bitcoin")
                },
                titleEnd = buildAnnotatedString {
                    append("\$44,403.13")
                },
                bodyStart = buildAnnotatedString {
                    append("BTC")
                },
                bodyEnd = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = AppTheme.colors.error)) {
                        append("↓ 12.32%")
                    }
                },
                startIconUrl = "https://www.blockchain.com/static/img/prices/prices-btc.svg",
                tags = listOf(
                    TagViewState(
                        value = "Completed",
                        type = TagType.Success
                    ),
                    TagViewState(
                        value = "Warning",
                        type = TagType.Warning
                    ),
                    TagViewState(
                        value = "Completed",
                        type = TagType.Success
                    ),
                    TagViewState(
                        value = "Warning",
                        type = TagType.Warning
                    ),
                    TagViewState(
                        value = "Completed",
                        type = TagType.Success
                    ),
                    TagViewState(
                        value = "Warning",
                        type = TagType.Warning
                    ),
                ),
                onClick = {},
            )
        }
    }
}

@Preview(name = "Stacked Icon", group = "Balance Row")
@Composable
fun BalanceStackedIconTableRowPreview() {
    AppTheme {
        AppSurface {
            BalanceStackedIconTableRow(
                titleStart = buildAnnotatedString {
                    append("Bitcoin")
                },
                titleEnd = buildAnnotatedString {
                    append("\$44,403.13")
                },
                bodyStart = buildAnnotatedString {
                    append("BTC")
                },
                bodyEnd = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = AppTheme.colors.error)) {
                        append("↓ 12.32%")
                    }
                },
                iconTopUrl = "https://www.blockchain.com/static/img/prices/prices-btc.svg",
                iconBottomUrl = "https://www.blockchain.com/static/img/prices/prices-eth.svg",
                onClick = {},
            )
        }
    }
}