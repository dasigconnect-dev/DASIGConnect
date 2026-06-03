# D2 — search golden-set results (UC-4.5)

Run 2026-06-03T22:18:50.012302900+08:00[Asia/Manila] · 20 queries · pageSize 8 · dataset `docs/eval/D2_search_golden_set.csv` (AI-inspected labels — verify before defense).

**Recall@3 = 1.000** (primary, target >= 0.70) · MRR = 1.000 · Recall@8 = 1.000

## By query type

| type | n | Recall@3 | MRR | Recall@8 |
|---|---|---|---|---|
| semantic | 8 | 1.000 | 1.000 | 1.000 |
| keyword | 6 | 1.000 | 1.000 | 1.000 |
| temporal | 4 | 1.000 | 1.000 | 1.000 |
| mixed | 2 | 1.000 | 1.000 | 1.000 |

## Per query

| id | type | query | R@3 | first-rel rank | top-3 |
|---|---|---|---|---|---|
| 1 | semantic | red velvet dessert with cream and white chocolate | ✅ | 1 | ASSET-03E180F4 ASSET-A70E026B ASSET-4B4B42ED |
| 2 | semantic | s'mores cookie with marshmallows and graham cracker | ✅ | 1 | ASSET-489A60E1 ASSET-5998691F ASSET-CEAC1C9F |
| 3 | semantic | matcha green tea dessert | ✅ | 1 | ASSET-CCB972E7 ASSET-2B9D50E4 ASSET-5857EA3A |
| 4 | semantic | people on stage at a hackathon award ceremony | ✅ | 1 | ASSET-A9A9A761 ASSET-F5212DF0 ASSET-3607DE9D |
| 5 | semantic | a person wearing a medical lab coat | ✅ | 1 | ASSET-88757CC3 ASSET-DE0FACFC ASSET-F6271DC7 |
| 6 | semantic | children playing soccer on a grass field | ✅ | 1 | ASSET-6498B163 ASSET-4DE95CFA ASSET-122A6F8B |
| 7 | semantic | close-up of a wild cat's face | ✅ | 1 | ASSET-79A1787C ASSET-D0E9F82A ASSET-88757CC3 |
| 8 | semantic | street food vendor at an outdoor market stall | ✅ | 1 | ASSET-218280AD ASSET-CDA3E760 ASSET-CEAC1C9F |
| 9 | keyword | ASSET-AB50C385 | ✅ | 1 | ASSET-AB50C385 ASSET-EEF8666C ASSET-ECCA1FB3 |
| 10 | keyword | Cebu Institute of Technology student ID | ✅ | 1 | ASSET-F7456D42 ASSET-6D1624C9 ASSET-9CF620E0 |
| 11 | keyword | OWASP Juice Shop | ✅ | 1 | ASSET-62FFC7DA ASSET-EEF8666C ASSET-CDA3E760 |
| 12 | keyword | GCash InstaPay QR code | ✅ | 1 | ASSET-AEB6355C ASSET-6D1624C9 ASSET-6D589471 |
| 13 | keyword | DASIGConnect logo branding | ✅ | 1 | ASSET-E08BC0E6 ASSET-F7456D42 ASSET-ECCA1FB3 |
| 14 | keyword | Oracle VirtualBox login screen | ✅ | 1 | ASSET-A632E0F5 ASSET-62FFC7DA ASSET-AEB6355C |
| 15 | temporal | photos uploaded on june 3 2026 | ✅ | 1 | ASSET-AB50C385 |
| 16 | temporal | pictures from june 2 2026 | ✅ | 1 | ASSET-7ED6A451 ASSET-D0B2B7C2 ASSET-6129F34F |
| 17 | temporal | uploaded on may 30 2026 | ✅ | 1 | ASSET-204707A1 ASSET-218280AD |
| 18 | temporal | 2026-06-02 | ✅ | 1 | ASSET-7ED6A451 ASSET-D0B2B7C2 ASSET-6129F34F |
| 19 | mixed | tennis photos uploaded on june 3 2026 | ✅ | 1 | ASSET-AB50C385 |
| 20 | mixed | portrait of a person with tattoos uploaded on june 2 2026 | ✅ | 1 | ASSET-3019D624 ASSET-729BF709 ASSET-ABB88AD6 |
