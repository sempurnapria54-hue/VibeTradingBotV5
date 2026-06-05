# Карта торгового корпуса

## На какой вопрос отвечает этот файл

Какие книги составляют торговый корпус, какое издание у каждой и
как по указателю «книга + глава + страница» из дистиллята прыгнуть
в нужное место PDF.

## Назначение

Дистиллят (`risk-and-sizing.md`, `system-design.md`,
`strategy-patterns.md`, `microstructure.md`) даёт тезисы с
указателями на источник. Эта карта сводит библиографию и правила
пересчёта страниц указателя в страницы PDF-файла. PDF — источник
правды для глубоких сверок.

## Книги и пересчёт страниц

| Короткое имя | Книга | Издание | Пагинация указателей | Пересчёт в страницу PDF |
|---|---|---|---|---|
| Vince | Ralph Vince. The Mathematics of Money Management: Risk Analysis Techniques for Traders | Wiley, 1992 | собственная пагинация этого PDF (печатная вёрстка Wiley в файле утрачена — перевёрстанная копия, 109 стр.) | страница указателя = страница PDF |
| Tharp | Van K. Tharp. Trade Your Way to Financial Freedom | McGraw-Hill, 1-е изд., 1999 | печатные страницы книги | PDF свёрстан по 2 печатные страницы на лист: PDF ≈ 14 + (печатная − 3) / 2; точно — поиском по тексту |
| Carver ST | Robert Carver. Systematic Trading: A unique new method for designing trading and investing systems | Harriman House, 2015 | eBook-пагинация (= номер в нижнем колонтитуле) | страница указателя = страница PDF (354 стр.) |
| Carver AFTS | Robert Carver. Advanced Futures Trading Strategies: 30 fully tested strategies | Harriman House, 2023 | eBook-конверсия без печатной пагинации | страница указателя = страница PDF (689 стр.) |
| Kaufman | Perry J. Kaufman. Trading Systems and Methods | Wiley, 6-е изд., 2019 | eBook-конверсия без печатной пагинации; указатели = глава + раздел + страница PDF | страница указателя = страница PDF (2285 стр.) |
| Harris | Larry Harris. Trading and Exchanges: Market Microstructure for Practitioners | Oxford University Press, 2003 | печатные страницы книги | PDF = печатная + 13 |

## Книги по пиллерам

| Пиллер | Основные источники |
|---|---|
| Риск и сайзинг | Vince (весь), Tharp (гл. 6, 9-12), Carver ST (гл. 9-10), Kaufman (гл. 23), Carver AFTS (тактика 4, стратегии 2-4) |
| Системный дизайн | Carver ST (весь каркас), Carver AFTS (ч. 1, тактики), Kaufman (гл. 1, 21, 22, 24), Tharp (гл. 4, 6-11) |
| Паттерны стратегий | Carver AFTS (30 стратегий), Kaufman (гл. 5, 8-20), Carver ST (гл. 7, прил. B), Tharp (гл. 5) |
| Микроструктура | Harris (весь); поддержка: Kaufman (гл. 16 — slippage), Carver AFTS (тактика 2 — исполнение) |
| Манипуляции (кросс-каттинг) | Harris (гл. 11-12, 28) → секции в `microstructure.md` и `risk-and-sizing.md` |

## Структура глав книг (якоря)

### Vince (страницы = PDF)

Введение 5; 1 Empirical Techniques 9; 2 Characteristics of Fixed
Fractional Trading 26; 3 Parametric Optimal f, Normal Distribution
35; 4 Parametric Techniques on Other Distributions 49; 5 Multiple
Simultaneous Positions 62; 6 Correlative Relationships / Efficient
Frontier 74; 7 Geometry of Portfolios 81; 8 Risk Management 89.

### Tharp (печатные страницы)

1 Holy Grail 3; 2 Judgmental Biases 17; 3 Objectives 45; 4 Steps to
Developing a System 61; 5 Selecting a Concept That Works 81;
6 Understanding Expectancy 130; 7 Setups 165; 8 Entry / Market
Timing 198; 9 Stops 233; 10 Profit-Taking Exits 254; 11 Opportunity
and Cost 270; 12 Position Sizing 280.

### Carver ST (страницы = PDF)

Гл. 1 Flawed Human Brain; 2 Systematic Trading Rules; 3 Fitting;
4 Portfolio Allocation; 5 Framework Overview; 6 Instruments;
7 Forecasts; 8 Combined Forecasts; 9 Volatility Targeting;
10 Position Sizing; 11 Portfolios; 12 Speed and Size;
13 Semi-automatic Trader; 14 Asset Allocating Investor; 15 Staunch
Systems Trader; прил. B Trading Rules (EWMAC, carry), C Portfolio
Optimisation, D Framework Details. Точные страницы — поиском.

### Carver AFTS (страницы = PDF)

Ч. 1 (стратегии 1-11): buy&hold → риск-скейлинг → тренд → carry;
ч. 2 (12-20): продвинутые тренд/carry, кросс-секционный моментум;
ч. 3 (21-25): breakout, value, acceleration, skew, dynamic
optimisation; ч. 4 (26-27): fast mean reversion; ч. 5 (28-30):
relative value; ч. 6: тактики 1-4 (роллы, исполнение, кэш,
риск-менеджмент); прил. B Calculations.

### Kaufman (страницы = PDF)

1 Introduction (guidelines, noise); 2 Basic Concepts; 3 Charting;
4 Charting Systems; 5 Event-Driven Trends (swing, P&F, N-day
breakout); 6 Regression; 7 Time-Based Trend; 8 Trend Systems;
9 Momentum and Oscillators; 10 Seasonality; 11 Cycles; 12 Volume;
13 Spreads and Arbitrage; 14 Behavioral (event trading, COT,
contrary opinion); 15 Short-Term Patterns; 16 Day Trading;
17 Adaptive; 18 Price Distribution (Market Profile); 19 Multiple
Time Frames; 20 Advanced (volatility, noise); 21 System Testing;
22 Adding Reality; 23 Risk Control; 24 Diversification and
Portfolio Allocation.

### Harris (печатные страницы; PDF = печатная + 13)

1 Introduction 3; 2 Trading Stories 11; 3 Trading Industry 32;
4 Orders 68; 5 Market Structures 89; 6 Order-driven Markets 112;
7 Brokers 139; 8 Why People Trade 176; 9 Good Markets 202;
10 Informed Traders 222; 11 Order Anticipators 245; 12 Bluffers and
Market Manipulation 259; 13 Dealers 278; 14 Bid/Ask Spreads 297;
15 Block Traders 322; 16 Value Traders 338; 17 Arbitrageurs 347;
18 Buy-Side Traders 380; 19 Liquidity 394; 20 Volatility 410;
21 Liquidity and Transaction Cost Measurement 420; 22 Performance
Evaluation 442; 23 Index and Portfolio Markets 484; 24 Specialists
494; 25 Internalization, Preferencing, Crossing 514; 26 Competition
524; 27 Floor vs Automated 543; 28 Bubbles, Crashes, and Circuit
Breakers 555; 29 Insider Trading 584.
