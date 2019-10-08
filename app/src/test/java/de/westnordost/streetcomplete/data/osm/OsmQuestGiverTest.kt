package de.westnordost.streetcomplete.data.osm

import org.junit.Before
import org.junit.Test

import java.util.Date

import de.westnordost.osmapi.map.data.Element
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.map.data.OsmNode
import de.westnordost.streetcomplete.any
import de.westnordost.streetcomplete.data.QuestStatus
import de.westnordost.streetcomplete.data.osm.persist.ElementGeometryDao
import de.westnordost.streetcomplete.data.osm.persist.OsmQuestDao
import de.westnordost.streetcomplete.data.osmnotes.OsmNoteQuestDao
import de.westnordost.streetcomplete.data.visiblequests.OrderedVisibleQuestTypesProvider
import de.westnordost.streetcomplete.mock
import de.westnordost.streetcomplete.on

import org.junit.Assert.*
import org.mockito.Mockito.verify

class OsmQuestGiverTest {

    private lateinit var osmNoteQuestDao: OsmNoteQuestDao
    private lateinit var osmQuestDao: OsmQuestDao
    private lateinit var osmQuestUnlocker: OsmQuestGiver
    private lateinit var questType: OsmElementQuestType<*>

    @Before fun setUp() {
        val elementGeometryDao: ElementGeometryDao = mock()
        on(elementGeometryDao.get(Element.Type.NODE, 1)).thenReturn(ElementPointGeometry(POS))

        osmNoteQuestDao = mock()
        on(osmNoteQuestDao.getAllPositions(any())).thenReturn(emptyList())

        osmQuestDao = mock()
        on(osmQuestDao.getAll(any())).thenReturn(emptyList())

        questType = mock()

        val questTypeProvider: OrderedVisibleQuestTypesProvider = mock()
        on(questTypeProvider.get()).thenReturn(listOf(questType))

        osmQuestUnlocker = OsmQuestGiver(osmNoteQuestDao, osmQuestDao, elementGeometryDao, questTypeProvider)
    }

    @Test fun `note blocks new quests`() {
        on(questType.isApplicableTo(NODE)).thenReturn(true)
        on(osmNoteQuestDao.getAllPositions(any())).thenReturn(listOf(POS))

        assertTrue(osmQuestUnlocker.updateQuests(NODE).createdQuests.isEmpty())
    }

    @Test fun `previous quest blocks new quest`() {
        val q = OsmQuest(questType, Element.Type.NODE, 1, ElementPointGeometry(POS))
        on(osmQuestDao.getAll(any())).thenReturn(listOf(q))
        on(questType.isApplicableTo(NODE)).thenReturn(true)

        val r = osmQuestUnlocker.updateQuests(NODE)
        assertTrue(r.createdQuests.isEmpty())
        assertTrue(r.removedQuestIds.isEmpty())
    }

    @Test fun `not applicable blocks new quest`() {
        on(questType.isApplicableTo(NODE)).thenReturn(false)

        val r = osmQuestUnlocker.updateQuests(NODE)
        assertTrue(r.createdQuests.isEmpty())
        assertTrue(r.removedQuestIds.isEmpty())
    }

    @Test fun `not applicable removes previous quest`() {
        val q = OsmQuest(123L, questType, Element.Type.NODE, 1, QuestStatus.NEW, null, null, Date(), ElementPointGeometry(POS))
        on(osmQuestDao.getAll(any())).thenReturn(listOf(q))
        on(questType.isApplicableTo(NODE)).thenReturn(false)

        val r = osmQuestUnlocker.updateQuests(NODE)
        assertTrue(r.createdQuests.isEmpty())
        assertEquals(123L, r.removedQuestIds.single())

        verify(osmQuestDao).deleteAllIds(listOf(123L))
    }

    @Test fun `applicable adds new quest`() {
        on(questType.isApplicableTo(NODE)).thenReturn(true)
        val r = osmQuestUnlocker.updateQuests(NODE)
        val quest = r.createdQuests.single()
        assertEquals(1, quest.elementId)
        assertEquals(Element.Type.NODE, quest.elementType)
        assertEquals(questType, quest.type)

        verify(osmQuestDao).deleteAll(any())
        verify(osmQuestDao).addAll(listOf(quest))
    }
}

private val POS = OsmLatLon(10.0, 10.0)
private val NODE = OsmNode(1, 0, POS, null, null, null)
