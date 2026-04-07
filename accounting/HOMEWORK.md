# Accounting homework - limity i blokady bez nowych bytow

Ponizej jest projekt rozwiazania dla wymagania: blokady srodkow i limity transakcyjne sa modelowane tylko przez `Account`, `Transaction`, `Entry`, `PostingRule`, `EligibilityCondition`, `AccountFinder` oraz odczyty z ksiegi.

## 1) Konta i ich rola

### Konta podstawowe (na kliencie)

- `CUSTOMER_AVAILABLE` (`ASSET`) - srodki dostepne do wydania.
- `CUSTOMER_BLOCKED` (`ASSET`) - srodki zablokowane po autoryzacji, przed finalnym rozliczeniem.
- `MERCHANT_SETTLEMENT` (`ASSET` albo `LIABILITY` zaleznie od planu kont) - finalny przeplyw rozliczeniowy.

Interpretacja sald:

- saldo `CUSTOMER_AVAILABLE` = ile klient moze aktualnie wydac,
- saldo `CUSTOMER_BLOCKED` = ile jest aktualnie zablokowane,
- suma obu odtwarza stan po autoryzacjach i rozliczeniach.

### Konta limitowe i audytowe (off-balance)

- `DAILY_LIMIT_USED` (`OFF_BALANCE`) - zuzycie limitu dziennego.
- `MONTHLY_LIMIT_USED` (`OFF_BALANCE`) - zuzycie limitu miesiecznego.
- `LIMIT_RENEWAL_AUDIT` (`OFF_BALANCE`) - slady resetu/odnowienia limitu.
- `LIMIT_EXCEEDED_AUDIT` (`OFF_BALANCE`) - przekroczenia i proby przekroczen.

Interpretacja sald:

- saldo/obroty na kontach limitowych sa "licznikami" wykorzystywanymi do odczytow okien czasowych,
- konto audytowe przechowuje fakt i skale prob przekroczen.

### Rozroznianie kont klienta bez nowych encji

Konta klienta sa rozpoznawane przez:

- nazwe konta,
- metadata transakcji (`customerId`, `cardId`, `limitScope`, itd.),
- ewentualnie przez konfiguracje reguly (`AccountFinder`) wskazujace konta po tagach/nazwach/typie.

## 2) Typy transakcji i zdarzenia biznesowe

- `hold_created` - utworzenie blokady (autoryzacja).
- `hold_settled` - rozliczenie blokady (pelne albo czesciowe).
- `hold_released` - zwolnienie/anulowanie blokady.
- `expiration_compensation` - wygasniecie blokady i kompensacja wpisu po `Validity`.
- `limit_consumed_daily` - rejestracja zuzycia limitu dziennego.
- `limit_consumed_monthly` - rejestracja zuzycia limitu miesiecznego.
- `limit_renewed` - reset/odnowienie limitu.
- `limit_exceeded_attempt` - przekroczenie lub proba przekroczenia (audyt jawny).
- `reversal` - odwracanie transakcji dla pelnej odtwarzalnosci.

## 3) Przebiegi ksiegowan (Entries)

### A) Blokada -> rozliczenie (z czesciowym rozliczeniem)

1. `hold_created`, kwota 300:
   - `DEBIT CUSTOMER_AVAILABLE 300`
   - `CREDIT CUSTOMER_BLOCKED 300`
2. `hold_settled`, kwota 200 (powiazanie przez `appliedTo` do wpisu blokady):
   - `DEBIT CUSTOMER_BLOCKED 200`
   - `CREDIT MERCHANT_SETTLEMENT 200`
3. `hold_released`, kwota 100 (reszta blokady):
   - `DEBIT CUSTOMER_BLOCKED 100`
   - `CREDIT CUSTOMER_AVAILABLE 100`

Sens biznesowy:

- najpierw przesuniecie dostepne -> zablokowane,
- potem czesc zablokowanych idzie do rozliczenia,
- reszta wraca klientowi.

Powiazanie zdarzen w czasie:

- `metadata.holdTxId = <id hold_created>`,
- `appliedTo = <EntryId wpisu CREDIT na CUSTOMER_BLOCKED>`.

### B) Blokada -> anulowanie/zwolnienie

1. `hold_created`:
   - `DEBIT CUSTOMER_AVAILABLE X`
   - `CREDIT CUSTOMER_BLOCKED X`
2. `hold_released`:
   - `DEBIT CUSTOMER_BLOCKED X`
   - `CREDIT CUSTOMER_AVAILABLE X`

Historia pozostaje w ksiedze, nic nie jest kasowane.

### C) Blokada wygasla

- blokada ma `Validity.until(T)`,
- po czasie `T` tworzymy `expiration_compensation` dla wpisu blokady,
- przyklad:
  - `DEBIT CUSTOMER_BLOCKED X` (zamkniecie niewykorzystanej blokady)
  - `CREDIT CUSTOMER_AVAILABLE X` (zwrot)

### D) Zuzycie limitu dziennego/miesiecznego

- `limit_consumed_daily`: `CREDIT DAILY_LIMIT_USED A`
- `limit_consumed_monthly`: `CREDIT MONTHLY_LIMIT_USED A`

To sa zapisy informacyjne (off-balance), ale audytowalne.

### E) Proba przekroczenia limitu

- `limit_exceeded_attempt`: `CREDIT LIMIT_EXCEEDED_AUDIT attemptedAmount`
- w metadata: `scope`, `limit`, `attemptedAmount`, `customerId`, `reason`.

Dzieki temu decyzja nie jest "cicha" - jest jawny slady w ksiedze.

## 4) Odwracalnosc i historia

- Odwrocenie: uzycie `ReverseTransactionCommand` tworzy `reversal` zamiast edycji in-place.
- Korekty: transakcja kompensujaca (np. dodatkowe `hold_released` albo nowy `hold_created`).
- Czesciowe rozliczenia: wielokrotne `hold_settled` z `appliedTo` do tej samej blokady.
- Wygasniecia: `expiration_compensation` wylicza pozostala niezamknieta wartosc wpisu.

Kazdy krok jest odtwarzalny tylko z transakcji i wpisow.

## 5) Widoki / odczyty z ksiegi

- Dostepne vs zablokowane:
  - `available = balance(CUSTOMER_AVAILABLE)`
  - `blocked = balance(CUSTOMER_BLOCKED)`
- Zuzycie limitow w oknie czasu:
  - suma dodatnich wpisow na `DAILY_LIMIT_USED` i `MONTHLY_LIMIT_USED` dla przedzialu `appliesAt`.
- Lista aktywnych blokad:
  - dla wpisow blokady na `CUSTOMER_BLOCKED` policzyc: `kwota_blokady - suma wpisow referujacych (appliedTo)`.
  - aktywna jest blokada z pozostala kwota > 0 i niewygasla.
- Zdarzenia przekroczen/prob:
  - wszystkie wpisy na `LIMIT_EXCEEDED_AUDIT` + metadata.

## Dodatkowo o automatyzacji (PostingRule)

Mozna dopiac reguly, ktore automatycznie tworza zapisy informacyjne, np.:

- gdy powstaje `hold_settled` na koncie klienta, regula dopisuje `limit_consumed_daily` i `limit_consumed_monthly`,
- `EligibilityCondition` filtruje po typie wpisu i koncie,
- `AccountFinder` wybiera docelowe konto limitowe.

W ten sposob decyzje i przeliczenia limitow pozostaja w tym samym mechanizmie ksiegowym.

