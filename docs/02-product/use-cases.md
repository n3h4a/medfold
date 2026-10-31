# Use cases

Each use case lists its acceptance criteria and the automated tests that verify them
(backend `*Test.java`, frontend `*.test.tsx` by description, end-to-end: the Postman collection).

## UC1: Upload a report

**Actor:** a signed-in user. **Goal:** get a document into Medfold.

| Acceptance criterion | Verified by |
|---|---|
| A PDF, JPG or PNG up to 10 MB is stored and appears in the timeline as "Waiting to process" | `DocumentUploadTest.uploadsPdfAndStoresFileAndRow`, UI "uploads valid files, rejects invalid ones instantly…" |
| The type comes from the file's content, not its name | `usesContentNotNameOrDeclaredTypeToDetectType`, `rejectsFilesThatOnlyPretendToBeSupported` |
| A file over 10 MB is refused with a clear message | `rejectsFilesOverTenMegabytes` |
| Uploading the same file again is refused and points to the existing document | `rejectsDuplicateForSameUserAndPointsToExistingDocument`, UI "explains server rejections such as duplicates" |
| Several files upload in parallel (at most 3 at a time) with per-file progress | UI "uploads at most three files at a time", "shows per-file progress" |

## UC2: Have it read automatically

| Acceptance criterion | Verified by |
|---|---|
| The document is read in the background and becomes READY with type, date, doctor, results and medicines | `DocumentPipelineTest.anUploadIsReadExtractedAndStored`, CI end-to-end seed of 8 sample documents |
| The page follows progress without reloading | UI "refreshes the list while documents are processing and stops once they are done" |
| A password-protected PDF fails at once with an explanation | `aProblemWithTheDocumentFailsAtOnceWithTheServicesMessage` |
| A document without readable text fails without calling the model | `aDocumentWithoutTextKeepsItsPagesAndFailsWithoutCallingTheModel` |
| If the AI service is down, the document is retried later, not lost | `anAiOutageIsRetriedLater`, `JobProcessingTest` |

## UC3: Review a document

| Acceptance criterion | Verified by |
|---|---|
| The original is shown next to the extracted details and summary | UI "shows details, summary and the PDF in an embedded viewer…" |
| Each lab value shows its printed range and a neutral comparison | `LabFlagCalculatorTest` (38 cases), UI "shows extracted test results compared with the printed range…" |
| Values that can't be compared with certainty get no comparison | `LabFlagCalculatorTest.anythingUncertainGetsNoFlag` |
| A failed document shows why, and can be tried again | UI "shows why processing failed", "lets the user try a failed document again" |

## UC4: Correct a misread value

| Acceptance criterion | Verified by |
|---|---|
| Any detail, lab result or medicine can be edited or removed | `EditingApiTest`, UI "corrects a misread lab value in place" |
| Correcting a value recomputes its comparison | `correctingALabValueRecomputesItsFlag` |
| Corrections survive processing the document again, without duplicates | `reprocessingKeepsResultsTheUserCorrectedWithoutDuplicatingThem`, `renamedCorrectionsAreStillRecognisedWhenReprocessing` |

## UC5: Follow a test over time

| Acceptance criterion | Verified by |
|---|---|
| Every test across reports is listed with its latest value | `RecordsApiTest.labTestsListEachTestOnceWithItsLatestValueAndTrendsAreOldestFirst` |
| The trend chart puts values in the latest unit, converting standard units and saying so | UI "keeps dated numeric values in the latest unit, converting standard units…", `units.test.ts` |
| Values in units that can't be converted are listed but not plotted | UI "leaves out values in a unit it has no standard conversion for" |

## UC6: Find something

| Acceptance criterion | Verified by |
|---|---|
| Any word in details, page text, test or medicine names finds the document, with a snippet | `SearchApiTest.findsWordsInPageTextWithASnippetAndThePage`, `findsDocumentDetailsTestsAndMedicinesByPartOfAWord` |
| Every word must match | `everyWordMustMatchSomewhereInTheSameDocument` |
| Another user's documents never appear | `neverReturnsAnotherUsersDocuments` |

## UC7: Take my data or leave

| Acceptance criterion | Verified by |
|---|---|
| Download everything as one JSON file | `AccountApiTest.exportContainsEverythingOfTheUserAndNothingOfOthers`, UI "downloads all data as a JSON file" |
| Deleting the account removes every row and file, after confirming the password | `deletingTheAccountRemovesEverythingOfThatUserOnly`, `aWrongOrMissingPasswordDeletesNothing` |

## UC8: Stay secure

| Acceptance criterion | Verified by |
|---|---|
| A session survives reloads and refreshes silently; one refresh is shared by parallel requests | UI "restores a session from the refresh cookie…", "refreshes once for many parallel requests…" |
| After 5 wrong passwords, even the right one is refused for a while | `LoginThrottleApiTest.afterFiveWrongPasswordsEvenTheRightOneIsRefusedForAWhile` |
| No user can read or change another user's data through any endpoint | `UserIsolationTest` |
